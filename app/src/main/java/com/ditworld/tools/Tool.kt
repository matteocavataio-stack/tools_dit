package com.ditworld.tools

data class Tool(
    val number: String,
    val title: String,
    val description: String,
    val url: String,
)

object Tools {
    private const val BASE = "https://ditworld.com/tools/"

    val all = listOf(
        Tool("01", "Camera specs & data rates",
            "Sensore, attacco, media e formati di registrazione con MB/s, GB al minuto e TB all'ora.",
            BASE + "cameras/"),
        Tool("02", "Firmware & software tracker",
            "Ultimi firmware delle cine camera e ultime versioni dei software DIT, con link ufficiali.",
            BASE + "firmware/"),
        Tool("03", "Data calculator",
            "Quanti TB servono per giorno e in totale, con copie e tempo di offload.",
            BASE + "data-calculator/"),
        Tool("04", "Lens coverage & equivalence",
            "Copertura dell'ottica sul sensore, campo visivo ed equivalenza focale. Sferiche e anamorfiche.",
            BASE + "lens-coverage/"),
        Tool("05", "Frame line calculator",
            "Pixel rimasti dentro una frame line (es. 9:16 da open gate, 2.39 da 16:9).",
            BASE + "frame-lines/"),
        Tool("06", "Exposure planner",
            "Cambia fps, otturatore, ND, luce o diaframma senza perdere esposizione. Angoli anti-flicker 50/60 Hz.",
            BASE + "exposure-planner/"),
        Tool("07", "Upload time calculator",
            "Quanto ci mette la tua giornata di girato a salire sul cloud a 10 Gbps, 1 Gbps o 100 Mbps.",
            BASE + "upload-time/"),
    )
}
