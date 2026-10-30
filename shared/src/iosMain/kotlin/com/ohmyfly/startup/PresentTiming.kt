package com.ohmyfly.startup

// Called from iosApp PresentTiming.swift, which hooks CAMetalLayer.nextDrawable. Main thread only.

/** Whether the current startup window still wants presented-time samples. */
fun startupPresentRecording(): Boolean = StartupRecords.current.presentSampling

/** presentedTime in CACurrentMediaTime seconds; 0 when the drawable was never shown. */
fun startupPresented(seconds: Double) = StartupRecords.current.samplePresent(seconds * 1000)
