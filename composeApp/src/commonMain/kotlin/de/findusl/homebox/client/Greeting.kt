package de.findusl.homebox.client

class Greeting {
	private val platform = getPlatform()

	fun greet(): String = "Hello, ${platform.name}!"
}
