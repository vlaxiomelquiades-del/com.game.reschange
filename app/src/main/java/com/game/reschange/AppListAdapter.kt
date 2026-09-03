package com.game.reschange

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Item generico da lista: ou um cabecalho de secao, ou um app de verdade.
sealed class ListItem {
    data class Header(val title: String) : ListItem()
    data class App(val appInfo: AppInfo) : ListItem()
}

class AppListAdapter(
    private var items: List<ListItem>,
    private val onClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }

    class HeaderViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val packageName: TextView = view.findViewById(R.id.packageName)
        val modifiedIcon: ImageView = view.findViewById(R.id.modifiedIcon)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.App -> TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(32, 40, 32, 16)
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(0xFF888888.toInt())
            }
            HeaderViewHolder(textView)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app, parent, false)
            AppViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> {
                (holder as HeaderViewHolder).textView.text = item.title
            }
            is ListItem.App -> {
                val app = item.appInfo
                val h = holder as AppViewHolder
                h.icon.setImageDrawable(app.icon)
                h.name.text = app.name
                h.packageName.text = app.packageName

                // Show gear icon if app's scale is modified
                val savedScale = ResChangePrefs.getScale(h.itemView.context, app.packageName)
                h.modifiedIcon.visibility = if (savedScale != 1.0f) View.VISIBLE else View.GONE

                h.itemView.setOnClickListener { onClick(app) }
            }
        }
    }

    fun submitList(newItems: List<ListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
