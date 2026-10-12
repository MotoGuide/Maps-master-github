package org.zarroboogs.maps.ui.tencent

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.zarroboogs.maps.R
import org.zarroboogs.maps.data.model.Profile

class MotorProfilePickerDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_PROFILES_COUNT = "profiles_count"
        private const val ARG_PROFILE_PREFIX = "profile_"

        @JvmStatic
        fun newInstance(profiles: List<Profile>): MotorProfilePickerDialog {
            return MotorProfilePickerDialog().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PROFILES_COUNT, profiles.size)
                    for ((i, p) in profiles.withIndex()) {
                        putString(ARG_PROFILE_PREFIX + i + "_name", p.name)
                        putInt(ARG_PROFILE_PREFIX + i + "_displacement", p.displacement)
                    }
                }
            }
        }
    }

    private var listener: OnProfileSelectedListener? = null

    fun setOnProfileSelectedListener(l: OnProfileSelectedListener) {
        listener = l
    }

    interface OnProfileSelectedListener {
        fun onProfileSelected(profileIndex: Int)
        fun onNoConfigSelected()
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        val activity = context as? Activity
        if (activity is OnProfileSelectedListener) {
            listener = activity
        }
        val parentFrag = parentFragment
        if (parentFrag is OnProfileSelectedListener) {
            listener = parentFrag
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DarkBottomSheetDialogTheme)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
        dialog?.findViewById<View>(com.google.android.material.R.id.container)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_motor_profile_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = view.findViewById<LinearLayout>(R.id.profile_list_container)
        val args = arguments ?: return
        val count = args.getInt(ARG_PROFILES_COUNT, 0)

        // Build profile buttons from arguments
        val profiles = mutableListOf<Pair<String, Int>>()
        for (i in 0 until count) {
            val name = args.getString(ARG_PROFILE_PREFIX + i + "_name") ?: "未知"
            val displacement = args.getInt(ARG_PROFILE_PREFIX + i + "_displacement", 0)
            profiles.add(name to displacement)
        }

        // Add each profile as a clickable card
        for ((index, profile) in profiles.withIndex()) {
            val card = createProfileCard(profile.first, profile.second)
            card.setOnClickListener {
                listener?.onProfileSelected(index)
                dismiss()
            }
            container.addView(card)
        }

        // "无配置（默认）" always present at bottom
        val noConfigCard = createNoConfigCard()
        noConfigCard.setOnClickListener {
            listener?.onNoConfigSelected()
            dismiss()
        }
        container.addView(noConfigCard)
    }

    private fun createProfileCard(name: String, displacement: Int): View {
        val card = TextView(requireContext()).apply {
            text = "$name · ${displacement}cc"
            textSize = 15f
            setTextColor(Color.BLACK)
            setPadding(36, 28, 36, 28)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.rounded_wellow_background)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.bottomMargin = 12
        card.layoutParams = params
        return card
    }

    private fun createNoConfigCard(): View {
        val card = TextView(requireContext()).apply {
            text = "无配置（默认）"
            textSize = 15f
            setTextColor(Color.parseColor("#888888"))
            setPadding(36, 24, 36, 24)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.rounded_dark_card_background)
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.layoutParams = params
        return card
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
