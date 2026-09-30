package com.example

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleRobolectricTest {

  @Test
  fun testMainActivityLaunches() {
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    assertNotNull(scenario)
    scenario.close()
  }
}
