package realm

import kotlinx.cinterop.CPointer
import objectstore_wrapper.realm_object_t

actual typealias BindingPointer = CPointerWrapper

class CPointerWrapper(val ptr : CPointer<realm_object_t>)