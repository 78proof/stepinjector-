package com.example

import android.app.Application
import com.example.data.repository.InjectorRepository
import com.example.worker.StepInjectionWorker

class StepInjectorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val repository = InjectorRepository(this)
        val settings = repository.settingsFlow.value
        if (settings.isRunning && settings.isDailyScheduleEnabled) {
            StepInjectionWorker.scheduleDailyWork(this)
        }
    }
}
