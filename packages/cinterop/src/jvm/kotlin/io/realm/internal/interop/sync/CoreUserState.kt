package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_user_state_e

actual enum class CoreUserState(
    override val nativeValue: Int
) : NativeEnumerated {

    RLM_USER_STATE_LOGGED_OUT(realm_user_state_e.RLM_USER_STATE_LOGGED_OUT),
    RLM_USER_STATE_LOGGED_IN(realm_user_state_e.RLM_USER_STATE_LOGGED_IN),
    RLM_USER_STATE_REMOVED(realm_user_state_e.RLM_USER_STATE_REMOVED);

    companion object {
        // TODO Optimize
        fun of(state: Int): CoreUserState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown user state: $state")
        }
    }
}
