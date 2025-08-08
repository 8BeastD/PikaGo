package com.leoworks.pikago.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.leoworks.pikago.R
import com.leoworks.pikago.models.ProfileSection
import com.leoworks.pikago.models.VerificationDocument

class ProfileSectionAdapter(
    private val onSectionClick: (ProfileSection) -> Unit
) : RecyclerView.Adapter<ProfileSectionAdapter.ProfileSectionViewHolder>() {

    private var sections = listOf<ProfileSection>()

    fun updateSections(newSections: List<ProfileSection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileSectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_section, parent, false)
        return ProfileSectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileSectionViewHolder, position: Int) {
        holder.bind(sections[position], onSectionClick)
    }

    override fun getItemCount(): Int = sections.size

    class ProfileSectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSectionTitle)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvSectionStatus)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressSection)
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivSectionIcon)

        fun bind(section: ProfileSection, onClick: (ProfileSection) -> Unit) {
            tvTitle.text = section.title
            progressBar.progress = section.progress

            if (section.isCompleted) {
                tvStatus.text = "Completed"
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
                ivIcon.setImageResource(R.drawable.ic_check_circle)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark))
            } else {
                tvStatus.text = "Incomplete"
                tvStatus.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark))
                ivIcon.setImageResource(R.drawable.ic_warning)
                ivIcon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark))
            }

            itemView.setOnClickListener {
                onClick(section)
            }
        }
    }
}

class DocumentAdapter(
    private val onDocumentClick: (String) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    private var documents = listOf<VerificationDocument>()
    private val allDocumentTypes = listOf(
        Pair("aadhar", "Aadhar Card"),
        Pair("pan", "PAN Card"),
        Pair("driving_license", "Driving License"),
        Pair("vehicle_registration", "Vehicle Registration"),
        Pair("vehicle_insurance", "Vehicle Insurance")
    )

    fun updateDocuments(newDocuments: List<VerificationDocument>) {
        documents = newDocuments
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val docType = allDocumentTypes[position]
        val existingDoc = documents.find { it.document_type == docType.first }
        holder.bind(docType, existingDoc, onDocumentClick)
    }

    override fun getItemCount(): Int = allDocumentTypes.size

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDocumentName: TextView = itemView.findViewById(R.id.tvDocumentName)
        private val tvDocumentStatus: TextView = itemView.findViewById(R.id.tvDocumentStatus)
        private val ivDocumentIcon: ImageView = itemView.findViewById(R.id.ivDocumentIcon)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.ivStatusIcon)

        fun bind(
            docType: Pair<String, String>,
            existingDoc: VerificationDocument?,
            onClick: (String) -> Unit
        ) {
            tvDocumentName.text = docType.second

            // Set document icon based on type
            when (docType.first) {
                "aadhar" -> ivDocumentIcon.setImageResource(R.drawable.ic_aadhar)
                "pan" -> ivDocumentIcon.setImageResource(R.drawable.ic_pan)
                "driving_license" -> ivDocumentIcon.setImageResource(R.drawable.ic_driving_license)
                "vehicle_registration" -> ivDocumentIcon.setImageResource(R.drawable.ic_vehicle_reg)
                "vehicle_insurance" -> ivDocumentIcon.setImageResource(R.drawable.ic_insurance)
                else -> ivDocumentIcon.setImageResource(R.drawable.ic_document)
            }

            if (existingDoc != null) {
                // Document exists, show status
                when (existingDoc.verification_status) {
                    "pending" -> {
                        tvDocumentStatus.text = "Under Review"
                        tvDocumentStatus.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                        )
                        ivStatusIcon.setImageResource(R.drawable.ic_hourglass)
                        ivStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                        )
                    }
                    "verified" -> {
                        tvDocumentStatus.text = "Verified"
                        tvDocumentStatus.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                        )
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        ivStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                        )
                    }
                    "rejected" -> {
                        tvDocumentStatus.text = "Rejected - Re-upload"
                        tvDocumentStatus.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                        )
                        ivStatusIcon.setImageResource(R.drawable.ic_error)
                        ivStatusIcon.setColorFilter(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                        )
                    }
                }
                ivStatusIcon.visibility = View.VISIBLE

                // Allow re-upload if rejected
                if (existingDoc.verification_status == "rejected") {
                    itemView.setOnClickListener { onClick(docType.first) }
                } else {
                    itemView.setOnClickListener(null)
                }
            } else {
                // Document not uploaded
                tvDocumentStatus.text = "Not Uploaded"
                tvDocumentStatus.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                )
                ivStatusIcon.setImageResource(R.drawable.ic_upload)
                ivStatusIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                )
                ivStatusIcon.visibility = View.VISIBLE

                itemView.setOnClickListener { onClick(docType.first) }
            }
        }
    }
}