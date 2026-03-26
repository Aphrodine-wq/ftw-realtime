package com.strata.ftw.service

import com.strata.ftw.domain.entity.FairRecord
import org.springframework.stereotype.Service

@Service
class FairRecordPdfService {

    fun generateCertificateHtml(record: FairRecord): String {
        val budgetStatus = if (record.onBudget) "On Budget" else "Over Budget"
        val timeStatus = if (record.onTime) "On Time" else "Delayed"
        val confirmedStatus = if (record.homeownerConfirmed) "Confirmed by Homeowner" else "Pending Confirmation"

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>FairRecord Certificate - ${record.publicId}</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 800px; margin: 0 auto; padding: 40px; color: #0F1419; }
                .header { text-align: center; border-bottom: 3px solid #059669; padding-bottom: 20px; margin-bottom: 30px; }
                .header h1 { color: #059669; font-size: 28px; margin: 0; }
                .header .id { color: #6B7280; font-size: 14px; margin-top: 8px; }
                .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin: 30px 0; }
                .metric { background: #F7F8FA; padding: 16px; border-radius: 8px; }
                .metric .label { font-size: 12px; color: #6B7280; text-transform: uppercase; letter-spacing: 0.5px; }
                .metric .value { font-size: 24px; font-weight: 700; margin-top: 4px; }
                .pass { color: #059669; }
                .fail { color: #DC2626; }
                .footer { text-align: center; margin-top: 40px; padding-top: 20px; border-top: 1px solid #E5E7EB; color: #6B7280; font-size: 12px; }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>FairRecord Certificate</h1>
                <div class="id">${record.publicId}</div>
            </div>
            <div class="grid">
                <div class="metric">
                    <div class="label">Category</div>
                    <div class="value">${record.category ?: "N/A"}</div>
                </div>
                <div class="metric">
                    <div class="label">Location</div>
                    <div class="value">${record.locationCity ?: "N/A"}</div>
                </div>
                <div class="metric">
                    <div class="label">Budget Accuracy</div>
                    <div class="value ${if (record.onBudget) "pass" else "fail"}">${String.format("%.1f", record.budgetAccuracyPct ?: 0.0)}% - $budgetStatus</div>
                </div>
                <div class="metric">
                    <div class="label">Timeline</div>
                    <div class="value ${if (record.onTime) "pass" else "fail"}">$timeStatus</div>
                </div>
                <div class="metric">
                    <div class="label">Average Rating</div>
                    <div class="value">${String.format("%.1f", record.avgRating)} / 5.0</div>
                </div>
                <div class="metric">
                    <div class="label">Reviews</div>
                    <div class="value">${record.reviewCount}</div>
                </div>
            </div>
            <div class="footer">
                <p>$confirmedStatus</p>
                <p>Verified by FairTradeWorker | fairtradeworker.com/record/${record.publicId}</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
