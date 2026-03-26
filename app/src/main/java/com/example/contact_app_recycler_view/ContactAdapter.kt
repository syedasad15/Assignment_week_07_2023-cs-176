package com.example.contact_app_recycler_view

import android.graphics.*
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(
    private val contactList: MutableList<Contact>,
    private val listener: OnContactActionListener
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    // Filtered list shown in the RecyclerView; starts as a copy of full list
    private val filteredList = mutableListOf<Contact>()

    // Palette of background colors for initial avatars, cycled by first-letter index
    private val avatarColors = listOf(
        Color.parseColor("#E53935"), // red
        Color.parseColor("#8E24AA"), // purple
        Color.parseColor("#1E88E5"), // blue
        Color.parseColor("#00897B"), // teal
        Color.parseColor("#F4511E"), // deep orange
        Color.parseColor("#3949AB"), // indigo
        Color.parseColor("#039BE5"), // light blue
        Color.parseColor("#43A047")  // green
    )

    init {
        filteredList.addAll(contactList)
    }

    interface OnContactActionListener {
        fun onItemClick(position: Int)
        fun onEditClick(contact: Contact, adapterPosition: Int)
        fun onDeleteClick(contact: Contact, adapterPosition: Int)
    }

    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView   = itemView.findViewById(R.id.ivAvatar)
        val tvContactName: TextView  = itemView.findViewById(R.id.tvContactName)
        val tvContactPhone: TextView = itemView.findViewById(R.id.tvContactPhone)
        val btnEdit: Button          = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button        = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.activity_item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = filteredList[position]

        holder.tvContactName.text  = contact.name
        holder.tvContactPhone.text = contact.phone

        // --- Profile picture logic ---
        if (contact.avatarUri != null) {
            // User picked a real image from gallery
            holder.ivAvatar.setImageURI(contact.avatarUri)
        } else {
            // Generate a colored circle with the contact's first initial
            holder.ivAvatar.setImageBitmap(generateInitialAvatar(contact.name))
        }

        // Pass the actual Contact object to listeners so position drift is a non-issue
        holder.itemView.setOnClickListener {
            listener.onItemClick(holder.bindingAdapterPosition)
        }
        holder.btnEdit.setOnClickListener {
            listener.onEditClick(contact, holder.bindingAdapterPosition)
        }
        holder.btnDelete.setOnClickListener {
            listener.onDeleteClick(contact, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = filteredList.size

    // -------------------------------------------------------------------------
    // Search — called by MainActivity whenever the search query changes
    // -------------------------------------------------------------------------
    fun filter(query: String) {
        filteredList.clear()
        if (query.isBlank()) {
            filteredList.addAll(contactList)
        } else {
            val q = query.trim().lowercase()
            filteredList.addAll(
                contactList.filter { contact ->
                    contact.name.lowercase().contains(q) ||
                    contact.phone.contains(q)
                }
            )
        }
        notifyDataSetChanged()
    }

    // -------------------------------------------------------------------------
    // Sorting — called by MainActivity from the sort button
    // -------------------------------------------------------------------------
    fun sortAZ() {
        contactList.sortBy { it.name.lowercase() }
        // Re-apply any active filter after sorting
        val currentFiltered = filteredList.map { it }.toMutableList()
        filteredList.clear()
        filteredList.addAll(
            contactList.filter { c -> currentFiltered.any { it === c } }
        )
        if (currentFiltered.size == contactList.size) {
            // No active filter; just show the sorted full list
            filteredList.clear()
            filteredList.addAll(contactList)
        }
        notifyDataSetChanged()
    }

    // -------------------------------------------------------------------------
    // Called by MainActivity after adding a new contact
    // -------------------------------------------------------------------------
    fun addContact(contact: Contact) {
        contactList.add(contact)
        // Only show in filtered list if it matches the current filter
        filteredList.add(contact)
        notifyItemInserted(filteredList.size - 1)
    }

    // -------------------------------------------------------------------------
    // Called by MainActivity after editing a contact
    // -------------------------------------------------------------------------
    fun notifyContactChanged(adapterPosition: Int) {
        notifyItemChanged(adapterPosition)
    }

    // -------------------------------------------------------------------------
    // Called by MainActivity to delete by reference (position-safe)
    // -------------------------------------------------------------------------
    fun deleteContact(contact: Contact, adapterPosition: Int) {
        contactList.remove(contact)
        filteredList.removeAt(adapterPosition)
        notifyItemRemoved(adapterPosition)
        notifyItemRangeChanged(adapterPosition, filteredList.size)
    }

    // -------------------------------------------------------------------------
    // Called after loading contacts from phone (replaces entire list)
    // -------------------------------------------------------------------------
    fun replaceAll(newList: List<Contact>) {
        contactList.clear()
        contactList.addAll(newList)
        filteredList.clear()
        filteredList.addAll(newList)
        notifyDataSetChanged()
    }

    // -------------------------------------------------------------------------
    // Generates a 96×96 bitmap: colored circle + white initial letter
    // -------------------------------------------------------------------------
    private fun generateInitialAvatar(name: String): Bitmap {
        val size    = 96
        val bitmap  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas  = Canvas(bitmap)

        // Pick color based on first character code so the same name always
        // gets the same color
        val initial = name.firstOrNull()?.uppercaseChar() ?: '?'
        val colorIndex = (initial.code % avatarColors.size).let {
            if (it < 0) it + avatarColors.size else it
        }
        val bgColor = avatarColors[colorIndex]

        // Draw circle
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bgColor
        val cx = size / 2f
        canvas.drawCircle(cx, cx, cx, paint)

        // Draw letter
        paint.color = Color.WHITE
        paint.textSize = size * 0.45f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        val textBounds = Rect()
        paint.getTextBounds(initial.toString(), 0, 1, textBounds)
        val textY = cx - textBounds.exactCenterY()

        canvas.drawText(initial.toString(), cx, textY, paint)

        return bitmap
    }
}
