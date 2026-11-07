package de.davscha.erzaehlwerk.i18n

/** Erzählwerk ist (vorerst) nur Deutsch – tr() setzt nur die Platzhalter ein. So passen übernommene Teile aus Character Fun. */
fun tr(text: String, vararg args: Any?): String = if (args.isEmpty()) text else String.format(text, *args)
