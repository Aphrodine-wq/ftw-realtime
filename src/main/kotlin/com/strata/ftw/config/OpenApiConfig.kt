package com.strata.ftw.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Public OpenAPI definition for the PriceGrid material price API.
 *
 * Buyers see this when they hit /swagger-ui (interactive explorer) or pull
 * the raw spec from /v3/api-docs. The shape, descriptions, and example
 * payloads are part of the product they're licensing — they should look
 * polished, not auto-generated.
 *
 * springdoc auto-discovers @RestController endpoints. We only need this
 * config to set the metadata block at the top of the generated spec.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun openApiSpec(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("PriceGrid — Material Price API")
                .version("1.0.0")
                .description(
                    """
                    Real-time multi-merchant building material prices, refreshed daily
                    from Google Shopping. One request returns prices from Home Depot,
                    Lowe's, Menards, Amazon, Walmart, and 270+ regional yards.

                    **Use cases**
                    - Estimate auto-fill (`/v1/prices/best/{material_key}`)
                    - Bulk pre-fetch when a project loads (`POST /v1/prices/lookup`)
                    - Day-over-day change detection (`/v1/prices/alerts/recent`)
                    - Pipeline observability (`/v1/prices/health`)

                    **Auth**
                    Read endpoints are currently public. The ingest endpoint
                    (`POST /v1/prices/ingest`) requires HMAC-SHA256 signing of the body
                    using a shared secret in the `X-FTW-Signature` header.

                    **Rate limits**
                    None enforced today. A multi-tenant deployment would add per-API-key
                    quotas at the gateway.
                    """.trimIndent()
                )
                .contact(
                    Contact()
                        .name("FairTradeWorker")
                        .email("api@fairtradeworker.com")
                )
                .license(
                    License()
                        .name("Proprietary — Source-Available")
                        .url("https://fairtradeworker.com/api-license")
                )
        )
        .addServersItem(Server().url("http://localhost:4000").description("Local dev"))
        .addServersItem(Server().url("https://api.pricegrid.app").description("Production (planned)"))
}
