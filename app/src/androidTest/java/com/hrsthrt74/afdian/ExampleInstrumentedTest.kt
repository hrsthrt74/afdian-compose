package com.hrsthrt74.afdian

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // 不能写死 "com.hrsthrt74.afdian"：debug 变体带 .debug 后缀、legacy 变体带 .legacy 后缀，
        // 各变体的 applicationId 不同。BuildConfig.APPLICATION_ID 是当前变体的真实包名。
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }
}