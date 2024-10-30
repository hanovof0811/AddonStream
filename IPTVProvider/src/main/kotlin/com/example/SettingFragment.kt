package com.example

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class SettingFragment(override val plugin: IPTVPlugin, nameFragment: String) : AbstractFragment(plugin,nameFragment) {
    lateinit var providers : MutableList<Link>
  val gson = Gson();
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        providers = getListLink(view.context)
        val recyclerView = view.findView<RecyclerView>("recyclerview")
        val btnAddLink  = view.findView<Button>("addLink")
        val adapter = LinkAdapter(plugin,view.context,this)
        val layoutAddLink = view.findView<View>("layoutAddLink")
        val btnXacNhan = view.findView<Button>("btnXacNhan")
        val confirm = view.findView<Button>("confirm")
        val name = view.findView<EditText>("etName")
        val link = view.findView<EditText>("etLink")
        recyclerView.layoutManager = LinearLayoutManager(view.context,RecyclerView.VERTICAL,false)
        adapter.notifyDataSetChanged()
        recyclerView.adapter = adapter
        btnAddLink.setOnClickListener(
            object : OnClickListener {
                override fun onClick(btn: View) {
                   layoutAddLink.visibility = View.VISIBLE
                    btnAddLink.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    confirm.visibility = View.GONE
                }
            }
        )
        btnXacNhan.setOnClickListener {
            if (name.text.isNullOrBlank()|| link.text.isNullOrBlank()) {
                Toast.makeText(view.context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            }else {
                addLink(name.text.toString(), link.text.toString())
                adapter.notifyDataSetChanged()
                layoutAddLink.visibility = View.GONE
                clearEditText(name, link)
                btnAddLink.visibility = View.VISIBLE
                confirm.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE
            }
        }
        confirm.setOnClickListener{
            val sharedPreferences = view.context.getSharedPreferences("IPTV", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val json = gson.toJson(providers)
            editor.putString("providers", json)
            editor.apply()
            plugin.reload(context,providers)
            dismiss()
        }
    }
    fun clearEditText(vararg editTexts: EditText) {
        for (editText in editTexts) {
            editText.setText("")
        }
    }
     fun removeLink(link: Link) {
        providers.remove(link)
    }
    private fun addLink( name: String, link: String) {
        providers.add(Link(name, link))
    }
    companion object {
        val gson = Gson()
        fun  getListLink(context: Context?) : MutableList<Link>{
            val json = context?.getSharedPreferences("IPTV", Context.MODE_PRIVATE)?.getString("providers", null)
            return if (json!=null){
                gson.fromJson<List<Link>>(json, object : TypeToken<List<Link>>() {}.type).toMutableList()
            }else {
                emptyList<Link>().toMutableList()
            }
        }
    }


}
