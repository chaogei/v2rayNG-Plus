package com.v2ray.ang.handler

/**
 * Pure decisions about the profile index that [MmkvManager] persists.
 */
internal object ProfileIndexMaintenance {

    /**
     * Every group index key currently held by the profile index store.
     *
     * Wiping all profiles has to reset all of them, not only the groups a subscription
     * still owns: an index left behind by a deleted subscription keeps listing GUIDs
     * whose payloads are already gone.
     */
    fun groupIndexKeys(storedKeys: Array<String>?, prefix: String): List<String> {
        return storedKeys.orEmpty().filter { key -> key.startsWith(prefix) }
    }

    /**
     * True when the stored selection points at a profile that is being removed, so the
     * selection key has to go with it. A dangling selection keeps the main screen marking
     * a node that no longer exists and makes the next start fail on an unresolvable GUID.
     */
    fun selectionRemoved(currentSelection: String?, removedGuids: Collection<String>): Boolean {
        if (currentSelection.isNullOrBlank()) return false
        return currentSelection in removedGuids
    }
}
