package com.clippost.app.captions

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Runs a Media3 export and reports progress. Both the caption burner and the vertical editor use it. */
@OptIn(UnstableApi::class)
object Exporter {

    suspend fun export(
        context: Context,
        composition: Composition,
        output: File,
        onProgress: suspend (Int) -> Unit
    ) {
        output.parentFile?.mkdirs()
        output.delete()

        // Transformer must be used on the main thread.
        withContext(Dispatchers.Main) {
            val finished = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        finished.completeExceptionally(exportException)
                    }
                })
                .build()

            transformer.start(composition, output.absolutePath)
            val holder = ProgressHolder()
            var lastPercent = -1
            try {
                while (!finished.isCompleted) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val percent = holder.progress.coerceIn(0, 100)
                        if (percent != lastPercent && percent % 5 == 0) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    delay(500)
                }
                finished.await() // throws if the export failed
            } catch (e: CancellationException) {
                transformer.cancel()
                throw e
            }
        }
    }
}
