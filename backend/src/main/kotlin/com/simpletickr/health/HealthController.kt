package com.simpletickr.health

import com.simpletickr.generated.api.DefaultApi
import com.simpletickr.generated.model.GetHealth200Response
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController : DefaultApi {
    override fun getHealth(): ResponseEntity<GetHealth200Response> =
        ResponseEntity.ok(GetHealth200Response(status = "UP"))
}