package com.mosquednd.ui.prayer

import android.util.Log
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mosquednd.R
import com.mosquednd.databinding.ItemPrayerTimeBinding
import com.mosquednd.prayer.PrayerAlarmPrefs
import com.mosquednd.prayer.PrayerTime
import com.mosquednd.prayer.PrayerType
import com.mosquednd.util.formatOffset

class PrayerTimesAdapter : ListAdapter<PrayerTime, PrayerTimesAdapter.ViewHolder>(DiffCallback()) {

    companion object {
        private const val TAG = "PrayerTimesAdapter"
    }
    private var nextIndex: Int = -1
    var expandedPosition: Int? = null
        private set
    var onToggleChanged: ((PrayerType, Boolean) -> Unit)? = null
    var onDNDToggleChanged: ((PrayerType, Boolean) -> Unit)? = null
    var onDNDOffsetChanged: ((PrayerType, Int) -> Unit)? = null
    var onDNDDurationChanged: ((PrayerType, Int) -> Unit)? = null
    var onCustomDurationSet: ((PrayerType, Int) -> Unit)? = null
    var onDndHelpClick: (() -> Unit)? = null
    var onPrayerHelpClick: ((PrayerType, View) -> Unit)? = null
    var onRowClick: ((PrayerType) -> Unit)? = null
    var onOffsetChanged: ((PrayerType, Int) -> Unit)? = null
    var dndTimeRanges: Map<PrayerType, String>? = null

    fun updateData(times: List<PrayerTime>, nextIndex: Int) {
        this.nextIndex = nextIndex; submitList(times)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPrayerTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == nextIndex, position,
            expandedPosition == position,
            onToggleChanged, onDNDToggleChanged, onDNDOffsetChanged, onDNDDurationChanged,
            onCustomDurationSet, onDndHelpClick,
            onRowClick, dndTimeRanges,
            onPrayerHelpClick, onOffsetChanged)
    }

    class ViewHolder(private val binding: ItemPrayerTimeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            prayerTime: PrayerTime, isNext: Boolean, position: Int,
            isExpanded: Boolean,
            toggleCallback: ((PrayerType, Boolean) -> Unit)?,
            dndToggleCallback: ((PrayerType, Boolean) -> Unit)?,
            dndOffsetCallback: ((PrayerType, Int) -> Unit)?,
            dndDurationCallback: ((PrayerType, Int) -> Unit)?,
            customDurationCallback: ((PrayerType, Int) -> Unit)?,
            helpClickCallback: (() -> Unit)?,
            rowClickCallback: ((PrayerType) -> Unit)?,
            dndTimeRanges: Map<PrayerType, String>?,
            prayerHelpClickCallback: ((PrayerType, View) -> Unit)?,
            offsetCallback: ((PrayerType, Int) -> Unit)?
        ) {
            val context = binding.root.context
            val type = prayerTime.type
            binding.textPrayerName.text = type.displayName
            binding.textPrayerTime.text = prayerTime.formattedTime

            if (type == PrayerType.SUNRISE) {
                binding.switchAlarmToggle.visibility = View.GONE
                binding.textOffsetIndicator.visibility = View.GONE
                binding.btnPrayerHelp.visibility = View.GONE
                binding.viewDndDivider.visibility = View.GONE
                binding.layoutDndSection.visibility = View.GONE
                binding.root.setOnClickListener(null)
            } else {
                binding.switchAlarmToggle.visibility = View.VISIBLE
                binding.btnPrayerHelp.visibility = View.VISIBLE
                binding.btnPrayerHelp.setOnClickListener {
                    prayerHelpClickCallback?.invoke(type, binding.btnPrayerHelp)
                }
                binding.btnPrayerHelp.contentDescription = "Help for ${type.displayName}"
                binding.switchAlarmToggle.setOnCheckedChangeListener(null)
                binding.switchAlarmToggle.isChecked = PrayerAlarmPrefs.isAlarmEnabled(type)
                val masterEnabled = PrayerAlarmPrefs.isMasterAlarmEnabled()
                binding.switchAlarmToggle.isEnabled = masterEnabled
                binding.switchAlarmToggle.alpha = if (masterEnabled) 1.0f else 0.4f
                binding.switchAlarmToggle.setOnCheckedChangeListener { _, isChecked ->
                    toggleCallback?.invoke(type, isChecked)
                }
                val offset = PrayerAlarmPrefs.getOffset(type)
                binding.textOffsetIndicator.text = formatOffset(offset)
                binding.textOffsetIndicator.visibility = View.VISIBLE
                binding.textOffsetIndicator.setOnClickListener {
                    offsetCallback?.invoke(type, offset)
                }

                if (isExpanded) {
                    showDndSection(type, dndToggleCallback, dndOffsetCallback, dndDurationCallback,
                        customDurationCallback, helpClickCallback, dndTimeRanges)
                } else {
                    hideDndSection()
                }
                binding.root.setOnClickListener {
                    rowClickCallback?.invoke(type)
                }
            }

            val stripColorRes = when (type) {
                PrayerType.FAJR -> R.color.prayer_strip_fajr
                PrayerType.SUNRISE -> R.color.prayer_strip_sunrise
                PrayerType.DHUHR -> R.color.prayer_strip_dhuhr
                PrayerType.ASR -> R.color.prayer_strip_asr
                PrayerType.MAGHRIB -> R.color.prayer_strip_maghrib
                PrayerType.ISHA -> R.color.prayer_strip_isha
            }
            binding.viewColorStrip.setBackgroundColor(ContextCompat.getColor(context, stripColorRes))

            if (isNext) {
                binding.badgeNext.visibility = View.VISIBLE
                binding.cardPrayer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.next_prayer_highlight))
                binding.cardPrayer.setStrokeColor(ContextCompat.getColor(context, R.color.gold_primary))
                binding.cardPrayer.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.next_prayer_stroke_width)
            } else {
                binding.badgeNext.visibility = View.GONE
                binding.cardPrayer.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_default))
                binding.cardPrayer.setStrokeColor(ContextCompat.getColor(context, android.R.color.transparent))
                binding.cardPrayer.strokeWidth = 0
            }
        }

        private fun showDndSection(
            type: PrayerType,
            dndToggleCallback: ((PrayerType, Boolean) -> Unit)?,
            dndOffsetCallback: ((PrayerType, Int) -> Unit)?,
            dndDurationCallback: ((PrayerType, Int) -> Unit)?,
            customDurationCallback: ((PrayerType, Int) -> Unit)?,
            helpClickCallback: (() -> Unit)?,
            dndTimeRanges: Map<PrayerType, String>?
        ) {
            binding.viewDndDivider.visibility = View.VISIBLE
            binding.layoutDndSection.visibility = View.VISIBLE

            // DND toggle
            binding.switchDndToggle.setOnCheckedChangeListener(null)
            binding.switchDndToggle.isChecked = PrayerAlarmPrefs.isDNDEnabled(type)
            binding.switchDndToggle.setOnCheckedChangeListener { _, isChecked ->
                dndToggleCallback?.invoke(type, isChecked)
            }

            // DND offset
            val currentOffset = PrayerAlarmPrefs.getDNDOffset(type)
            binding.seekbarDndOffset.setOnSeekBarChangeListener(null)
            binding.seekbarDndOffset.progress = currentOffset + 60
            binding.textDndOffsetValue.text = formatDndOffsetValue(currentOffset)
            binding.seekbarDndOffset.contentDescription = binding.root.context.getString(R.string.dnd_seekbar_content_desc, formatDndOffsetValue(currentOffset))
            binding.seekbarDndOffset.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val offset = progress - 60
                        binding.textDndOffsetValue.text = formatDndOffsetValue(offset)
                        binding.seekbarDndOffset.contentDescription = binding.root.context.getString(R.string.dnd_seekbar_content_desc, formatDndOffsetValue(offset))
                        // Visual update only — do NOT invoke callback here (causes rebind loop)
                    }
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    val offset = binding.seekbarDndOffset.progress - 60
                    dndOffsetCallback?.invoke(type, offset)
                }
            })

            // Help button
            binding.btnDndHelp.setOnClickListener {
                helpClickCallback?.invoke()
            }

            // DND duration spinner — reset listener before setting up
            binding.spinnerDndDuration.onItemSelectedListener = null
            binding.layoutCustomDuration.visibility = View.GONE

            // Wire custom set button
            binding.btnCustomDurationSet.setOnClickListener {
                val text = binding.editCustomDuration.text.toString().trim()
                val minutes = text.toIntOrNull()
                if (minutes == null || minutes < 1 || minutes > 999) {
                    Toast.makeText(binding.root.context, binding.root.context.getString(R.string.dnd_custom_invalid), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                customDurationCallback?.invoke(type, minutes)
                Toast.makeText(binding.root.context, binding.root.context.getString(R.string.dnd_custom_value_set, minutes), Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Custom DND duration for $type set to $minutes")
            }

            // Populate spinner and select current value
            val durationValues = binding.root.context.resources.getStringArray(R.array.dnd_duration_preset_values)
            val adapter = android.widget.ArrayAdapter(binding.root.context, android.R.layout.simple_spinner_dropdown_item, durationValues.toList())
            binding.spinnerDndDuration.adapter = adapter

            val currentDuration = PrayerAlarmPrefs.getDNDDuration(type)
            val spinnerPosition = when (currentDuration) {
                5 -> 0; 10 -> 1; 15 -> 2; 20 -> 3; 30 -> 4; 60 -> 5
                else -> 6 // "Custom…" position
            }
            binding.spinnerDndDuration.setSelection(spinnerPosition)

            // Set listener AFTER setting selection to avoid re-entry
            binding.spinnerDndDuration.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                    val duration = when (pos) {
                        0 -> 5; 1 -> 10; 2 -> 15; 3 -> 20; 4 -> 30; 5 -> 60
                        6 -> null // Custom
                        else -> null
                    }
                    if (duration != null) {
                        dndDurationCallback?.invoke(type, duration)
                        binding.layoutCustomDuration.visibility = View.GONE
                    } else {
                        // Custom selected — show custom input
                        binding.layoutCustomDuration.visibility = View.VISIBLE
                        binding.editCustomDuration.setText(
                            PrayerAlarmPrefs.getDNDDuration(type).toString()
                        )
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

            // DND time range
            val rangeText = dndTimeRanges?.get(type)
            if (rangeText != null) {
                binding.textDndTimeRange.text = rangeText
                binding.textDndTimeRange.visibility = View.VISIBLE
            } else {
                binding.textDndTimeRange.visibility = View.GONE
            }
        }

        private fun hideDndSection() {
            binding.viewDndDivider.visibility = View.GONE
            binding.layoutDndSection.visibility = View.GONE
        }


        private fun formatDndOffsetValue(offset: Int): String {
            return if (offset > 0) "+${offset} min"
            else if (offset < 0) "$offset min"
            else "0 min"
        }


    }

    fun toggleExpanded(prayerType: PrayerType) {
        val position = getPositionForType(prayerType) ?: return
        expandedPosition = if (expandedPosition == position) null else position
        notifyDataSetChanged()
    }

    fun collapseExpanded() {
        if (expandedPosition != null) { expandedPosition = null; notifyDataSetChanged() }
    }

    private fun getPositionForType(prayerType: PrayerType): Int? {
        for (i in 0 until itemCount) { if (getItem(i).type == prayerType) return i }
        return null
    }

    class DiffCallback : DiffUtil.ItemCallback<PrayerTime>() {
        override fun areItemsTheSame(oldItem: PrayerTime, newItem: PrayerTime): Boolean = oldItem.type == newItem.type
        override fun areContentsTheSame(oldItem: PrayerTime, newItem: PrayerTime): Boolean = oldItem == newItem
    }
}
