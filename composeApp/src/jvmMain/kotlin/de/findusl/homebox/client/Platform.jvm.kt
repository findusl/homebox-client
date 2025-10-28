package de.findusl.homebox.client

class JVMPlatform : Platform {
	override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()
