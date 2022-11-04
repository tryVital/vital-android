package io.tryvital.sample

import io.tryvital.client.services.data.User

class UserRepository private constructor(var selectedUser: User?) {

    companion object {
        fun create(): UserRepository = UserRepository(null)
    }

}