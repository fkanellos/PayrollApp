package com.fkcoding.PayrollApp.app.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 🔍 Client Matching Service
 * Handles client name matching logic for calendar events
 */
@Service
class ClientMatchingService {

    companion object {
        private val logger = LoggerFactory.getLogger(ClientMatchingService::class.java)
    }

    /**
     * Find client matches for a given event title
     *
     * Matching strategies (in order):
     * 1. Special keywords match (if provided)
     * 2. Full name match (exact substring)
     * 3. Reversed name match (e.g., "John Doe" -> "Doe John")
     * 4. Surname match (word boundary, min 4 chars)
     * 5. First name match (word boundary, min 4 chars)
     * 6. Dash-separated name parts (e.g., "John - Γιάννης")
     *
     * @param title Event title to match against
     * @param clientNames List of client names to search
     * @param specialKeywords Special keywords that override normal matching
     * @return List of matching client names
     */
    fun findClientMatches(
        title: String,
        clientNames: List<String>,
        specialKeywords: List<String> = emptyList()
    ): List<String> {
        if (title.isBlank()) return emptyList()

        // Normalize title (lowercase + remove Greek accents)
        val titleLower = normalizeText(title)
        val matches = mutableListOf<String>()

        // Strategy 1: Special keywords (e.g., "supervision")
        for (keyword in specialKeywords) {
            if (normalizeText(keyword) in titleLower) {
                matches.add(keyword)
                return matches
            }
        }

        // Match against client names
        for (clientName in clientNames) {
            if (clientName.isBlank()) continue

            val clientLower = normalizeText(clientName)
            val nameParts = clientLower.split(" ").filter { it.isNotBlank() }

            // Strategy 2: Full name match
            if (clientLower in titleLower) {
                matches.add(clientName)
                continue
            }

            // If single name, try direct match
            if (nameParts.size < 2) {
                if (nameParts.first() in titleLower) {
                    matches.add(clientName)
                }
                continue
            }

            // Strategy 3: Reversed name match
            val reversedName = "${nameParts.last()} ${nameParts.first()}"
            if (reversedName in titleLower) {
                matches.add(clientName)
                continue
            }

            // Strategy 4: Surname match (min 4 chars)
            val surname = nameParts.last()
            if (surname.length > 3) {
                val regex = "\\b${Regex.escape(surname)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            // Strategy 5: First name match (min 4 chars)
            val firstName = nameParts.first()
            if (firstName.length > 3) {
                val regex = "\\b${Regex.escape(firstName)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            // Strategy 6: Dash-separated names (e.g., "Ndrekaj Ornela - Ντρεκαι Ορνελα")
            if ("-" in clientName) {
                val parts = clientName.split("-").map { normalizeText(it.trim()) }
                for (part in parts) {
                    if (part in titleLower) {
                        matches.add(clientName)
                        break
                    }
                }
            }
        }

        return matches
    }

    /**
     * Normalize text for matching (lowercase + remove Greek accents)
     */
    private fun normalizeText(text: String): String {
        return text.lowercase().trim()
            .replace("ά", "α").replace("έ", "ε")
            .replace("ή", "η").replace("ί", "ι")
            .replace("ό", "ο").replace("ύ", "υ")
            .replace("ώ", "ω")
    }

    /**
     * Find client matches with detailed debugging information
     * Used for debugging and testing matching logic
     */
    fun findClientMatchesDebug(title: String, clientNames: List<String>): Map<String, Any> {
        if (title.isBlank()) {
            return mapOf(
                "title" to title,
                "matches" to emptyList<String>(),
                "details" to "Empty title"
            )
        }

        val titleLower = normalizeText(title)
        val matches = mutableListOf<String>()
        val details = mutableListOf<String>()

        details.add("Testing title: '$title'")
        details.add("Normalized: '$titleLower'")

        for (clientName in clientNames) {
            if (clientName.isBlank()) continue

            val clientLower = normalizeText(clientName)
            val nameParts = clientLower.split(" ").filter { it.isNotBlank() }

            details.add("\nTesting against client: '$clientName'")
            details.add("  Normalized: '$clientLower'")

            // Test 1: Full name match
            if (clientLower in titleLower) {
                details.add("  ✅ MATCH: Full name found in title")
                matches.add(clientName)
                continue
            }

            if (nameParts.size < 2) {
                if (nameParts.first() in titleLower) {
                    details.add("  ✅ MATCH: Single name found")
                    matches.add(clientName)
                } else {
                    details.add("  ⚠️  Single name, no match")
                }
                continue
            }

            // Test 2: Reversed name
            val reversedName = "${nameParts.last()} ${nameParts.first()}"
            if (reversedName in titleLower) {
                details.add("  ✅ MATCH: Reversed name found")
                matches.add(clientName)
                continue
            }

            // Test 3: Surname only
            val surname = nameParts.last()
            if (surname.length > 3) {
                val regex = "\\b${Regex.escape(surname)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    details.add("  ✅ MATCH: Surname '$surname' found")
                    matches.add(clientName)
                    continue
                }
            }

            // Test 4: First name only
            val firstName = nameParts.first()
            if (firstName.length > 3) {
                val regex = "\\b${Regex.escape(firstName)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    details.add("  ✅ MATCH: First name '$firstName' found")
                    matches.add(clientName)
                    continue
                }
            }

            // Test 5: Dash-separated names
            if ("-" in clientName) {
                val parts = clientName.split("-").map { normalizeText(it.trim()) }
                var dashMatch = false
                for (part in parts) {
                    if (part in titleLower) {
                        details.add("  ✅ MATCH: Dash-separated part '$part' found")
                        matches.add(clientName)
                        dashMatch = true
                        break
                    }
                }
                if (dashMatch) continue
            }

            details.add("  ❌ NO MATCH")
        }

        return mapOf(
            "title" to title,
            "titleNormalized" to titleLower,
            "matches" to matches,
            "matchCount" to matches.size,
            "details" to details
        )
    }
}
