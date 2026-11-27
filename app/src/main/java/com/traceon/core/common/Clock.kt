package com.traceon.core.common

fun interface Clock { fun now(): Long }

object WallClock : Clock { override fun now(): Long = System.currentTimeMillis() }
