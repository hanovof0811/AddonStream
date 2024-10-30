package com.example

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button

import android.widget.EditText
import android.widget.Switch
import androidx.recyclerview.widget.RecyclerView

import com.lagradost.cloudstream3.plugins.Plugin

class LinkAdapter(val plugin: Plugin,val context: Context,val settingFragment: SettingFragment) : RecyclerView.Adapter<LinkAdapter.LinkViewHolder>() {

    inner class LinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView = itemView.findView<EditText>("title")
        val linkTextView = itemView.findView<EditText>("link")
        val deleteButton = itemView.findView<Button>("delete")
        val editButton = itemView.findView<Button>("edit")
        val isActive = itemView.findView<Switch>("isActive")
        fun <T : View> View.findView(name: String): T {
            val id = plugin.resources!!.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            return this.findViewById(id)
        }
        init {
            setListeners()
        }

        private fun setListeners() {
            isActive.setOnCheckedChangeListener { buttonView, isChecked ->

                   settingFragment.providers[adapterPosition].isActive = isChecked

                }
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    removeData(position)
                }

            }

            editButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val link = settingFragment.providers[position]
                    if (editButton.text.equals("Sửa")) {
                        editButton.text = "Lưu"
                        titleTextView.isEnabled = true
                        linkTextView.isEnabled = true
                        deleteButton.visibility = View.GONE
                    }else{
                        deleteButton.visibility = View.VISIBLE
                        editButton.text = "Sửa"
                        titleTextView.isEnabled = false
                        linkTextView.isEnabled = false
                        val title = titleTextView.text.toString()
                        link.link = linkTextView.text.toString()
                        link.title = title
                        notifyItemChanged(position)
                    }
                }
            }
        }
    }
    fun removeData(position: Int) {

        settingFragment.removeLink(settingFragment.providers[position])
        notifyItemRemoved(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkViewHolder {
        val id = plugin.resources!!.getIdentifier(
            "item_link",
            "layout",
            BuildConfig.LIBRARY_PACKAGE_NAME
        )
        val layout = plugin.resources!!.getLayout(id)
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return LinkViewHolder(view)
    }

    override fun getItemCount(): Int {
        return if (settingFragment.providers!=null) settingFragment.providers.size else 0
    }

    override fun onBindViewHolder(holder: LinkViewHolder, position: Int) {
        val link = settingFragment.providers[position] ?: return
        holder.titleTextView.setText(link.title)
        holder.linkTextView.setText(link.link)
        holder.isActive.isChecked = link.isActive
    }
}