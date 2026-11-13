package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.InventoryItem
import com.example.model.ScanAnalysisResult
import com.example.model.ScanType
import com.example.model.ScannedItemCandidate
import com.example.model.UserPersona
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

private const val TAG = "InventoryScanner"
private const val INFERENCE_TIMEOUT_MS = 60_000L
private const val MAX_IMAGE_DIMENSION = 1440

class InventoryScannerService {

    private val scanSchema = Schema.obj(
        properties = mapOf(
            "detectedSourceLabel" to Schema.string(
                description = "Vendor/store name on receipt, or fridge/shelf area description"
            ),
            "summary" to Schema.string(
                description = "Brief 1-sentence summary of what was detected in the photo"
            ),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Clean, normalized product or ingredient name"),
                        "category" to Schema.string(
                            description = "Category such as Produce, Dairy, Meat & Seafood, Bakery, Pantry, Beverages, Frozen, Spices, Supplies"
                        ),
                        "quantity" to Schema.double(description = "Numeric quantity detected (e.g. 2.0, 0.5, 12.0)"),
                        "unit" to Schema.string(description = "Unit of measure: pcs, kg, g, L, ml, boxes, bottles, packs"),
                        "unitCost" to Schema.double(description = "Estimated price per single unit in USD (0.0 if unknown)"),
                        "estimatedShelfLifeDays" to Schema.integer(
                            description = "Estimated days until expiration from today (0 if non-perishable or unknown)"
                        ),
                        "suggestedMinThreshold" to Schema.double(
                            description = "Recommended minimum par threshold before restock alert triggers"
                        ),
                        "actionHint" to Schema.enumeration(
                            values = listOf("add", "restock", "update_count"),
                            description = "Suggested inventory action based on scan type"
                        )
                    )
                ),
                description = "List of distinct food, ingredient, or stock items detected in the image"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scanSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun analyzePhoto(
        bitmap: Bitmap,
        scanType: ScanType,
        persona: UserPersona,
        existingItems: List<InventoryItem>
    ): Result<ScanAnalysisResult> {
        val scaledBitmap = scaleBitmapDown(bitmap, MAX_IMAGE_DIMENSION)
        val existingNamesHint = existingItems
            .take(40)
            .joinToString(", ") { "${it.name} (${it.quantity} ${it.unit})" }
            .ifBlank { "None yet" }

        val modeInstructions = when (scanType) {
            ScanType.RECEIPT -> """
                This photo is a purchase receipt or supplier invoice.
                Extract every purchased grocery, ingredient, or supply line item.
                Read item quantities and unit prices carefully (divide line total by quantity for unitCost if needed).
                Estimate realistic shelf life in days for perishable items (e.g., Dairy 7-10 days, Produce 4-7 days, Meat 3-5 days, Dry Pantry 180-365 days, Supplies 0).
                Set actionHint to "add" or "restock".
            """.trimIndent()
            ScanType.FRIDGE -> """
                This photo is inside a refrigerator, walk-in cooler, or pantry shelf.
                Identify all visible foods, produce, bottles, containers, and ingredients.
                Estimate remaining quantities and realistic remaining shelf life days based on food type and apparent freshness.
                Set actionHint to "update_count".
            """.trimIndent()
            ScanType.STOCK -> """
                This photo shows retail shelves, dry storage racks, or stock boxes.
                Count visible units/packs/cases for each SKU or product type.
                Suggest practical minThreshold par levels for a ${persona.title} operation.
                Set actionHint to "update_count".
            """.trimIndent()
        }

        val prompt = """
            You are an expert digital inventory vision system for a ${persona.title} manager.
            $modeInstructions
            Existing inventory items in this location (match exact names when clearly the same item): $existingNamesHint.
            Return accurate, clean item names without receipt abbreviations where obvious (e.g. "ORG WHL MLK" -> "Organic Whole Milk").
        """.trimIndent()

        return try {
            val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val root = JSONObject(jsonText)
            val sourceLabel = root.optString("detectedSourceLabel", scanType.title)
            val summary = root.optString("summary", "Items extracted from ${scanType.title.lowercase()} photo.")
            val itemsJson = root.optJSONArray("items")

            val candidates = buildList {
                if (itemsJson != null) {
                    for (i in 0 until itemsJson.length()) {
                        val obj = itemsJson.getJSONObject(i)
                        val name = obj.optString("name", "").trim()
                        if (name.isBlank()) continue
                        val matched = existingItems.firstOrNull {
                            it.name.equals(name, ignoreCase = true)
                        }
                        add(
                            ScannedItemCandidate(
                                tempId = UUID.randomUUID().toString(),
                                name = name,
                                category = obj.optString("category", "Pantry").ifBlank { "Pantry" },
                                quantity = obj.optDouble("quantity", 1.0).coerceAtLeast(0.1),
                                unit = obj.optString("unit", "pcs").ifBlank { "pcs" },
                                unitCost = obj.optDouble("unitCost", 0.0).coerceAtLeast(0.0),
                                estimatedShelfLifeDays = obj.optInt("estimatedShelfLifeDays", 0).coerceAtLeast(0),
                                suggestedMinThreshold = obj.optDouble("suggestedMinThreshold", 1.0).coerceAtLeast(0.0),
                                actionHint = obj.optString("actionHint", "add"),
                                matchedExistingItemId = matched?.id,
                                selected = true
                            )
                        )
                    }
                }
            }

            Result.success(
                ScanAnalysisResult(
                    scanType = scanType,
                    detectedSourceLabel = sourceLabel,
                    summary = summary,
                    items = candidates
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI photo scan timed out", e)
            Result.failure(
                IllegalStateException("Photo analysis timed out after 60 seconds. Try a clearer or closer photo.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI photo scan failed", e)
            Result.failure(e)
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
