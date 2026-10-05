package com.drivelink.core.domain.model

import com.drivelink.core.domain.ContractFiles
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.IndexEntry
import com.drivelink.core.domain.error.Problem
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Contract test: every example in api/examples decodes into the app models with [DriveLinkJson].
 * 2xx bodies decode as the success type of the operation. 4xx and 5xx bodies decode as [Problem].
 */
class ContractTest {

    /** The success status and body type of one operation. [serializer] is null when there is no body. */
    private data class Success(val status: Int, val serializer: KSerializer<*>?)

    private val successTypes: Map<String, Success> = mapOf(
        "getHealth" to Success(200, Health.serializer()),
        "createToken" to Success(200, TokenResponse.serializer()),
        "refreshToken" to Success(200, TokenResponse.serializer()),
        "getMe" to Success(200, User.serializer()),
        "listVehicles" to Success(200, ListSerializer(Vehicle.serializer())),
        "getVehicleStatus" to Success(200, VehicleStatus.serializer()),
        "sendCommand" to Success(202, Command.serializer()),
        "getCommand" to Success(200, Command.serializer()),
        "getVehicleLocation" to Success(200, Location.serializer()),
        "getChargeSettings" to Success(200, ChargeSettings.serializer()),
        "updateChargeSettings" to Success(200, ChargeSettings.serializer()),
        "getClimatePresets" to Success(200, ClimatePresets.serializer()),
        "updateClimatePresets" to Success(200, ClimatePresets.serializer()),
        "listTrips" to Success(200, ListSerializer(Trip.serializer())),
        "getMaintenance" to Success(200, Maintenance.serializer()),
        "createServiceRequest" to Success(201, ServiceRequest.serializer()),
        "listAlerts" to Success(200, ListSerializer(Alert.serializer())),
        "markAlertRead" to Success(204, null),
    )

    @Test
    fun `table covers every operation in the index and nothing else`() {
        val indexOperations = ContractFiles.index.map { it.operationId }.toSet()
        assertThat(successTypes).hasSize(18)
        assertThat(indexOperations).containsExactlyElementsIn(successTypes.keys)
    }

    @Test
    fun `every example decodes, re-encodes without loss and round-trips`() {
        val failures = mutableListOf<String>()
        var decoded = 0
        val decodedSuccessOperations = mutableSetOf<String>()

        for (entry in ContractFiles.index.filter { it.file != null && !it.invalidOnPurpose }) {
            val result = runCatching { checkExample(entry) }
            result.exceptionOrNull()?.let { failures += "${entry.file}: ${it.message}" }
            if (result.isSuccess) {
                decoded++
                if (entry.status < 400) decodedSuccessOperations += entry.operationId
            }
        }

        println("Contract test: decoded $decoded examples from api/examples/index.json")
        assertWithMessage(failures.joinToString("\n")).that(failures).isEmpty()
        val expected = ContractFiles.index.count { it.file != null && !it.invalidOnPurpose }
        assertThat(decoded).isEqualTo(expected)
        assertThat(decoded).isAtLeast(100)
        // Every operation with a response body has at least one success example.
        assertThat(decodedSuccessOperations)
            .containsExactlyElementsIn(successTypes.filterValues { it.serializer != null }.keys)
    }

    @Test
    fun `examples marked invalidOnPurpose fail to decode`() {
        val invalid = ContractFiles.index.filter { it.invalidOnPurpose }
        assertThat(invalid).isNotEmpty()
        for (entry in invalid) {
            val serializer = serializerFor(entry)
            assertThrows(entry.file, SerializationException::class.java) {
                DriveLinkJson.decodeFromString(serializer, ContractFiles.read(entry))
            }
        }
    }

    @Test
    fun `raw bad-payload body fails to decode as VehicleStatus`() {
        val raw = ContractFiles.rawDir.resolve("getVehicleStatus.bad-payload.json").readText()
        assertThrows(SerializationException::class.java) {
            DriveLinkJson.decodeFromString(VehicleStatus.serializer(), raw)
        }
    }

    @Test
    fun `each bad-payload defect alone fails to decode`() {
        val good = ContractFiles.index.single {
            it.operationId == "getVehicleStatus" && it.status == 200 && it.example == "default"
        }
        val goodJson = DriveLinkJson.parseToJsonElement(ContractFiles.read(good)).jsonObject
        // Sanity check: the unchanged body decodes.
        DriveLinkJson.decodeFromJsonElement(VehicleStatus.serializer(), goodJson)

        val missingRange = JsonObject(goodJson - "rangeMi")
        val lockedAsString = JsonObject(goodJson + ("locked" to JsonPrimitive("yes")))
        for (body in listOf(missingRange, lockedAsString)) {
            assertThrows(SerializationException::class.java) {
                DriveLinkJson.decodeFromString(VehicleStatus.serializer(), body.toString())
            }
        }
    }

    @Test
    fun `request bodies omit absent optional fields`() {
        val request = CommandRequest(type = CommandType.LOCK, pin = "1234")
        assertThat(DriveLinkJson.encodeToString(CommandRequest.serializer(), request))
            .isEqualTo("""{"type":"LOCK","pin":"1234"}""")

        val start = CommandRequest(
            type = CommandType.START,
            pin = "1234",
            params = ClimateParams(tempMode = TempMode.OFF, durationMin = 5),
        )
        assertThat(DriveLinkJson.encodeToString(CommandRequest.serializer(), start))
            .isEqualTo("""{"type":"START","pin":"1234","params":{"tempMode":"OFF","durationMin":5}}""")
    }

    private fun serializerFor(entry: IndexEntry): KSerializer<Any?> {
        val success = successTypes[entry.operationId]
            ?: error("Operation ${entry.operationId} is not in the success table")
        val serializer = when {
            entry.status >= 400 -> Problem.serializer()
            entry.status == success.status -> success.serializer
                ?: error("Status ${entry.status} of ${entry.operationId} has no body, but the index has a file")
            else -> error("Status ${entry.status} of ${entry.operationId} is not in the success table")
        }
        @Suppress("UNCHECKED_CAST")
        return serializer as KSerializer<Any?>
    }

    private fun checkExample(entry: IndexEntry) {
        val serializer = serializerFor(entry)
        val text = ContractFiles.read(entry)
        val value = DriveLinkJson.decodeFromString(serializer, text)

        if (value is Problem) {
            assertWithMessage("problem status").that(value.status).isEqualTo(entry.status)
        }

        // No field is lost: the re-encoded value has the same JSON as the file.
        val original = normalize(DriveLinkJson.parseToJsonElement(text))
        val encoded = normalize(DriveLinkJson.encodeToJsonElement(serializer, value))
        assertWithMessage("re-encoded JSON").that(encoded).isEqualTo(original)

        // Round trip: encode and decode again gives an equal value.
        val again = DriveLinkJson.decodeFromString(serializer, DriveLinkJson.encodeToString(serializer, value))
        assertWithMessage("round trip").that(again).isEqualTo(value)
    }

    /** Drops nulls and writes every number in one canonical form, so 7 and 7.0 compare equal. */
    private fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterValues { it != JsonNull }.mapValues { normalize(it.value) })
        is JsonArray -> JsonArray(element.map(::normalize))
        JsonNull -> element
        is JsonPrimitive -> when {
            element.isString || element.booleanOrNull != null -> element
            else -> JsonPrimitive(element.content.toBigDecimal().stripTrailingZeros())
        }
    }
}
