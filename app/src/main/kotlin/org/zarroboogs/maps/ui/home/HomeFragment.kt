package org.zarroboogs.maps.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.zarroboogs.maps.R

class HomeFragment : Fragment() {

    private var overviewFragment: VehicleOverviewFragment? = null
    private var detailFragment: VehicleDetailFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            showOverview()
        } else {
            overviewFragment =
                childFragmentManager.findFragmentByTag(TAG_OVERVIEW) as? VehicleOverviewFragment
            detailFragment =
                childFragmentManager.findFragmentByTag(TAG_DETAIL) as? VehicleDetailFragment
        }
    }

    fun showOverview() {
        val frag = VehicleOverviewFragment()
        overviewFragment = frag
        childFragmentManager.beginTransaction()
            .replace(R.id.child_fragment_container, frag, TAG_OVERVIEW)
            .commit()
        detailFragment = null
    }

    fun showDetail(profileName: String) {
        val frag = VehicleDetailFragment.newInstance(profileName)
        detailFragment = frag
        childFragmentManager.beginTransaction()
            .replace(R.id.child_fragment_container, frag, TAG_DETAIL)
            .addToBackStack(TAG_DETAIL)
            .commit()
    }

    fun onBackPressedFromChild(): Boolean {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            detailFragment = null
            return true
        }
        return false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            detailFragment?.onContainerHidden()
        } else {
            detailFragment?.onContainerShown()
        }
    }

    companion object {
        private const val TAG_OVERVIEW = "overview"
        private const val TAG_DETAIL = "detail"
    }
}
