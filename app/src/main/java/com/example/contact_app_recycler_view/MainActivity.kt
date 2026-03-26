package com.example.contact_app_recycler_view

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity(), ContactAdapter.OnContactActionListener {

    // ── UI references ──────────────────────────────────────────────────────
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var btnLoadContacts: Button
    private lateinit var btnSortAZ: Button
    private lateinit var etSearch: EditText
    private lateinit var recyclerViewContacts: RecyclerView

    // ── Data & adapter ──────────────────────────────────────────────────────
    private lateinit var contactAdapter: ContactAdapter
    private val contactList = mutableListOf<Contact>()

    // ── Tracks which contact is being edited (to assign picked photo) ───────
    private var pendingPhotoContact: Contact? = null
    private var pendingPhotoDialogAvatar: ImageView? = null

    // ── Activity-result launchers ───────────────────────────────────────────

    /** Runtime permission for reading device contacts */
    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadContactsFromPhone()
            else Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_SHORT).show()
        }

    /** Opens the photo gallery; result is a content URI for the chosen image */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                pendingPhotoContact?.avatarUri = it
                pendingPhotoDialogAvatar?.setImageURI(it)
            }
        }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Bind views
        etName               = findViewById(R.id.etName)
        etPhone              = findViewById(R.id.etPhone)
        btnSave              = findViewById(R.id.btnSave)
        btnLoadContacts      = findViewById(R.id.btnLoadContacts)
        btnSortAZ            = findViewById(R.id.btnSortAZ)
        etSearch             = findViewById(R.id.etSearch)
        recyclerViewContacts = findViewById(R.id.recyclerViewContacts)

        // RecyclerView setup
        contactAdapter = ContactAdapter(contactList, this)
        recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        recyclerViewContacts.adapter = contactAdapter

        // Search — filter on every keystroke
        etSearch.addTextChangedListener { editable ->
            contactAdapter.filter(editable?.toString() ?: "")
        }

        // Buttons
        btnSave.setOnClickListener         { saveContact() }
        btnLoadContacts.setOnClickListener { checkPermissionAndLoadContacts() }
        btnSortAZ.setOnClickListener       { contactAdapter.sortAZ() }
    }

    // ── Save new contact ────────────────────────────────────────────────────

    private fun saveContact() {
        val name  = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()

        if (!validateInputs(name, phone, etName, etPhone)) return

        val newContact = Contact(name, phone)
        contactAdapter.addContact(newContact)
        recyclerViewContacts.scrollToPosition(contactAdapter.itemCount - 1)
        Toast.makeText(this, "Contact saved successfully", Toast.LENGTH_SHORT).show()

        etName.text.clear()
        etPhone.text.clear()
        etName.requestFocus()
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private fun validateInputs(
        name: String, phone: String,
        nameInput: EditText, phoneInput: EditText
    ): Boolean {
        var ok = true

        if (name.isEmpty()) {
            nameInput.error = "Name is required"
            ok = false
        }
        if (phone.isEmpty()) {
            phoneInput.error = "Phone number is required"
            ok = false
        } else if (phone.length < 10 || !phone.all { it.isDigit() || it == '+' }) {
            phoneInput.error = "Enter a valid phone number"
            ok = false
        }
        return ok
    }

    // ── Adapter callbacks ───────────────────────────────────────────────────

    override fun onItemClick(position: Int) {
        // No-op for now; could open a detail screen
    }

    override fun onEditClick(contact: Contact, adapterPosition: Int) {
        showEditDialog(contact, adapterPosition)
    }

    override fun onDeleteClick(contact: Contact, adapterPosition: Int) {
        showDeleteDialog(contact, adapterPosition)
    }

    // ── Delete dialog ───────────────────────────────────────────────────────

    private fun showDeleteDialog(contact: Contact, adapterPosition: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Delete ${contact.name}?")
            .setPositiveButton("Yes") { _, _ ->
                contactAdapter.deleteContact(contact, adapterPosition)
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ── Edit dialog (with profile picture picker) ───────────────────────────

    private fun showEditDialog(contact: Contact, adapterPosition: Int) {
        val dialogView  = LayoutInflater.from(this).inflate(R.layout.activity_dialog_edit_item, null)
        val etEditName  = dialogView.findViewById<EditText>(R.id.etEditName)
        val etEditPhone = dialogView.findViewById<EditText>(R.id.etEditPhone)
        val ivEditAvatar = dialogView.findViewById<ImageView>(R.id.ivEditAvatar)
        val btnPickPhoto = dialogView.findViewById<Button>(R.id.btnPickPhoto)

        // Pre-fill fields
        etEditName.setText(contact.name)
        etEditPhone.setText(contact.phone)

        // Show current avatar (real URI or auto-generated initial)
        if (contact.avatarUri != null) {
            ivEditAvatar.setImageURI(contact.avatarUri)
        } else {
            // Will be replaced once adapter generates the bitmap; show placeholder
            ivEditAvatar.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Wire up gallery picker — store references so the launcher callback
        // can write back into this dialog without capturing a potentially
        // closed dialog view
        pendingPhotoContact     = contact
        pendingPhotoDialogAvatar = ivEditAvatar

        btnPickPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit Contact")
            .setView(dialogView)
            .setPositiveButton("Update", null)   // null prevents auto-dismiss
            .setNegativeButton("Cancel") { _, _ ->
                // Clear pending refs if user cancels
                pendingPhotoContact      = null
                pendingPhotoDialogAvatar = null
            }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val updatedName  = etEditName.text.toString().trim()
            val updatedPhone = etEditPhone.text.toString().trim()

            if (validateInputs(updatedName, updatedPhone, etEditName, etEditPhone)) {
                contact.name  = updatedName
                contact.phone = updatedPhone
                // avatarUri was already written by pickImageLauncher callback
                contactAdapter.notifyContactChanged(adapterPosition)
                Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show()
                pendingPhotoContact      = null
                pendingPhotoDialogAvatar = null
                dialog.dismiss()
            }
        }
    }

    // ── Permission + load contacts from phone ───────────────────────────────

    private fun checkPermissionAndLoadContacts() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> loadContactsFromPhone()

            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) ->
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("This app needs access to your contacts to display them.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Deny", null)
                    .show()

            else -> requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContactsFromPhone() {
        val loaded = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )

        cursor?.use {
            val nameIdx  = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val n = it.getString(nameIdx)  ?: ""
                val p = it.getString(phoneIdx) ?: ""
                if (n.isNotBlank() && p.isNotBlank()) loaded.add(Contact(n, p))
            }
        }

        if (loaded.isEmpty()) {
            Toast.makeText(this, "No contacts found on your phone", Toast.LENGTH_SHORT).show()
            return
        }

        contactAdapter.replaceAll(loaded)
        Toast.makeText(this, "${loaded.size} contacts loaded", Toast.LENGTH_SHORT).show()
    }
}
