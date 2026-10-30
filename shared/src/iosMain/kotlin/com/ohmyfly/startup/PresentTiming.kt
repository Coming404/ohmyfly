package com.ohmyfly.startup

// Called from iosApp PresentTiming.swift, which wraps Compose's CMPMetalLayer. Main thread only.

/** Swift wrapped CMPMetalLayer. */
fun startupPresentHookInstalled() = StartupRecords.current.markPresentHookInstalled()

/** Whether the current startup window still wants samples. */
fun startupPresentRecording(): Boolean = StartupRecords.current.presentSampling

/** CMPMetalLayer replaced its contents at this CACurrentMediaTime, in seconds. */
fun startupPresented(seconds: Double) = StartupRecords.current.samplePresent(seconds * 1000)

/** The render thread waited waitSeconds in CMPMetalLayer.nextDrawable, ending at endSeconds. */
fun startupDrawableWaited(endSeconds: Double, waitSeconds: Double) =
    StartupRecords.current.sampleDrawableWait(endSeconds * 1000, waitSeconds * 1000)
