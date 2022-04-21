package io.realm

import io.realm.internal.ObjectIdImpl

/**
 *
 * A globally unique identifier for objects.
 *
 *
 * Consists of 12 bytes, divided as follows:
 * <table border="1">
 * <caption>ObjectID layout</caption>
 * <tr>
 * <td>0</td><td>1</td><td>2</td><td>3</td><td>4</td><td>5</td><td>6</td><td>7</td><td>8</td><td>9</td><td>10</td><td>11</td>
</tr> *
 * <tr>
 * <td colspan="4">time</td><td colspan="5">random value</td><td colspan="3">inc</td>
</tr> *
</table> *
 *
 *
 */

public interface ObjectId : Comparable<ObjectId> {
    public companion object {
        public fun from(hexString: String): ObjectId = ObjectIdImpl(hexString)

        public fun from(date: RealmInstant): ObjectId = ObjectIdImpl(date)

        @OptIn(ExperimentalUnsignedTypes::class)
        public fun from(bytes: UByteArray): ObjectId = ObjectIdImpl(bytes)

        public fun get(): ObjectId = ObjectIdImpl()
    }
}
