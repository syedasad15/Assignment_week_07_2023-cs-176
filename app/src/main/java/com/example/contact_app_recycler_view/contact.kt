package com.example.contact_app_recycler_view

import android.net.Uri

data class Contact(
    var name: String,
    var phone: String,
    var avatarUri: Uri? = null   // null = use generated initial avatar
)

