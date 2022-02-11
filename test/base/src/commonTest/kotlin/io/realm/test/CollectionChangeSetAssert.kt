package io.realm.test

import io.realm.notifications.ListChange
import io.realm.notifications.UpdatedList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun assertContains(array: IntArray, element: ListChange.Range) {
    for (value in element.startIndex until element.startIndex + element.length) {
        kotlin.test.assertContains(
            array,
            value,
            "Array [${array.joinToString(",")}] does not contain `$value`"
        )
    }
}

private fun assertContains(
    expectedRanges: Array<ListChange.Range>,
    indices: IntArray,
    ranges: Array<ListChange.Range>
) {
    if (expectedRanges.isEmpty()) {
        assertTrue(indices.isEmpty())
        assertTrue(ranges.isEmpty())
    } else {
        var elementCount = 0

        for (range in expectedRanges) {
            assertContains(indices, range)
            assertContains(ranges, range)

            elementCount += range.length
        }

        assertEquals(elementCount, indices.size)
    }
}

fun assertIsChangeSet(
    updatedListChange: UpdatedList<*>,
    insertRanges: Array<ListChange.Range> = emptyArray(),
    deletionRanges: Array<ListChange.Range> = emptyArray(),
    changesRanges: Array<ListChange.Range> = emptyArray()
) {
    assertContains(
        insertRanges,
        updatedListChange.insertions,
        updatedListChange.insertionRanges
    )

    assertContains(
        deletionRanges,
        updatedListChange.deletions,
        updatedListChange.deletionRanges
    )

    assertContains(
        changesRanges,
        updatedListChange.changes,
        updatedListChange.changeRanges
    )
}
