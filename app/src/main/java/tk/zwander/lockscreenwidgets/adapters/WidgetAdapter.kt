package tk.zwander.lockscreenwidgets.adapters

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import kotlinx.android.synthetic.main.widget_item.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.WidgetListInfo

class WidgetAdapter(private val picasso: Picasso, private val selectionCallback: (provider: WidgetListInfo) -> Unit) :
    RecyclerView.Adapter<WidgetAdapter.WidgetVH>() {
    private val widgets = SortedList(WidgetListInfo::class.java, object : SortedList.Callback<WidgetListInfo>() {
        override fun areItemsTheSame(item1: WidgetListInfo?, item2: WidgetListInfo?): Boolean {
            return false
        }

        override fun areContentsTheSame(oldItem: WidgetListInfo?, newItem: WidgetListInfo?): Boolean {
            return false
        }

        override fun compare(o1: WidgetListInfo, o2: WidgetListInfo): Int {
            return o1.compareTo(o2)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int) {
            notifyItemRangeChanged(position, count)
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }
    })

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        WidgetVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.widget_item,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: WidgetVH, position: Int) {
        holder.itemView
            .setOnClickListener { selectionCallback.invoke(widgets.get(holder.adapterPosition)) }
        holder.parseInfo(widgets.get(holder.adapterPosition), picasso)
    }

    override fun getItemCount() = widgets.size()

    fun addItem(item: WidgetListInfo) {
        widgets.add(item)
        notifyDataSetChanged()
    }

    class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        fun parseInfo(info: WidgetListInfo, picasso: Picasso) {
            itemView.widget_name.text = info.widgetName

            val img = itemView.widget_image

            picasso.cancelRequest(img)
            picasso
                .load("${RemoteResourcesIconHandler.SCHEME}://${info.appInfo.packageName}/${info.previewImg}")
                .resize(img.maxWidth, img.maxHeight)
                .onlyScaleDown()
                .centerInside()
                .into(img, object : Callback {
                    override fun onError(e: Exception?) {
                        picasso
                            .load(Uri.parse("${AppIconRequestHandler.SCHEME}:${info.appInfo.packageName}"))
                            .resize(img.maxWidth, img.maxHeight)
                            .onlyScaleDown()
                            .into(img)
                    }

                    override fun onSuccess() {}
                })
        }
    }

    class AppIconRequestHandler(context: Context) : RequestHandler() {
        companion object {
            const val SCHEME = "package"
        }

        private val pm = context.packageManager

        override fun canHandleRequest(data: Request): Boolean {
            return (data.uri != null && data.uri.scheme == SCHEME)
        }

        override fun load(request: Request, networkPolicy: Int): Result? {
            val pName = request.uri.schemeSpecificPart

            val img = pm.getApplicationIcon(pName).mutate().toBitmap()
                .run { copy(config, false) }

            return Result(img, Picasso.LoadedFrom.DISK)
        }
    }

    class RemoteResourcesIconHandler(context: Context) : RequestHandler() {
        companion object {
            const val SCHEME = "remote_res_widget"
        }

        private val pm = context.packageManager

        override fun canHandleRequest(data: Request): Boolean {
            return (data.uri != null && data.uri.scheme == SCHEME)
        }

        override fun load(request: Request, networkPolicy: Int): Result? {
            val pathSegments = request.uri.pathSegments

            val pName = request.uri.host
            val id = pathSegments[0].toInt()

            val img = pm.getResourcesForApplication(pName).getDrawable(id)
                .mutate().toBitmap().run { copy(config, false) }

            return Result(img, Picasso.LoadedFrom.DISK)
        }
    }
}