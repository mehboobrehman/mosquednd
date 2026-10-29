package com.mosquednd.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mosquednd.ui.home.HomeFragment
import com.mosquednd.ui.prayer.PrayerTimesFragment
import com.mosquednd.ui.qibla.ComposeQiblaFragment

class MosquesPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> MosquesFragment()
            2 -> PrayerTimesFragment()
            3 -> ComposeQiblaFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}
