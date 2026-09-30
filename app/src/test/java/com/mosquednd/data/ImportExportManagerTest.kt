package com.mosquednd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ImportExportManager.parseImportJson] version validation.
 *
 * These tests exercise only the success and strict-version-mismatch code paths,
 * which do not touch [android.util.Log] (the JVM stub throws "Stub!"). Malformed
 * JSON is deliberately not tested here because that path logs via android.util.Log
 * and requires an instrumented/Robolectric environment.
 */
class ImportExportManagerTest {

    /**
     * Minimal well-formed export JSON with a configurable schema version.
     * Only the fields needed by the version check are populated.
     */
    private fun exportJson(version: Int): String {
        return """{"exportVersion":$version,"exportedAt":"2026-07-22T00:00:00Z","calculationMethod":"mwl","asrMethod":"standard","highLatitudeMethod":"none","masterAlarmEnabled":true,"themePreference":"system"}"""
    }

    @Test
    fun testCurrentVersionParsesSuccessfully() {
        val result = ImportExportManager.parseImportJson(exportJson(1))

        assertTrue("Current schema version (1) must parse successfully", result.isSuccess)
        assertEquals(1, result.getOrThrow().exportVersion)
    }

    @Test
    fun testNewerVersionIsRejected() {
        val result = ImportExportManager.parseImportJson(exportJson(2))

        assertTrue("A newer schema version must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImportExportManager.VersionMismatchException)
        assertEquals(2, (result.exceptionOrNull() as ImportExportManager.VersionMismatchException).foundVersion)
        assertEquals(1, (result.exceptionOrNull() as ImportExportManager.VersionMismatchException).expectedVersion)
    }

    @Test
    fun testMuchNewerVersionIsRejected() {
        val result = ImportExportManager.parseImportJson(exportJson(5))

        assertTrue("A much newer schema version must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImportExportManager.VersionMismatchException)
    }

    @Test
    fun testZeroVersionIsRejected() {
        val result = ImportExportManager.parseImportJson(exportJson(0))

        assertTrue("A zero schema version must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImportExportManager.VersionMismatchException)
    }

    @Test
    fun testNegativeVersionIsRejected() {
        val result = ImportExportManager.parseImportJson(exportJson(-1))

        assertTrue("A negative schema version must be rejected", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImportExportManager.VersionMismatchException)
    }

    @Test
    fun testVersionMismatchMessageStatesSupportedVersion() {
        val result = ImportExportManager.parseImportJson(exportJson(3))

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        // The supported version number must be explicitly named in the error.
        assertTrue(
            "Error message must name the supported version number 1: \"$message\"",
            message.contains("version 1")
        )
    }

    @Test
    fun testVersionMismatchMessageStatesUnsupportedVersion() {
        val result = ImportExportManager.parseImportJson(exportJson(3))

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(
            "Error message must name the unsupported version number 3: \"$message\"",
            message.contains("version 3")
        )
    }

    @Test
    fun testValidImportDoesNotFailForCurrentVersionOnly() {
        // The exact-supported-version case must succeed even when only the version
        // and a single setting are present (other fields use Gson defaults).
        val minimalJson = """{"exportVersion":1,"calculationMethod":"isna"}"""
        val result = ImportExportManager.parseImportJson(minimalJson)

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
    }
}
