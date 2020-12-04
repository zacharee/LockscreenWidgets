package tk.zwander.lockscreenwidgets.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetListInfo
import tk.zwander.lockscreenwidgets.databinding.WidgetItemBinding

/**
 * Adapter to host widget previews in [AddWidgetActivity] for a specific app.
 */
class AddWidgetAdapter(private val picasso: Picasso, private val selectionCallback: (provider: WidgetListInfo) -> Unit) :
    RecyclerView.Adapter<AddWidgetAdapter.WidgetVH>() {
    private val widgets = AsyncListDiffer(this, object : DiffUtil.ItemCallback<WidgetListInfo>() {
        override fun areContentsTheSame(oldItem: WidgetListInfo, newItem: WidgetListInfo): Boolean {
            return false
        }

        override fun areItemsTheSame(oldItem: WidgetListInfo, newItem: WidgetListInfo): Boolean {
            return false
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
            .setOnClickListener { selectionCallback.invoke(widgets.currentList[holder.adapterPosition]) }
        holder.parseInfo(widgets.currentList[holder.adapterPosition], picasso)
    }

    override fun getItemCount() = widgets.currentList.size

    fun setItems(items: List<WidgetListInfo>) {
        widgets.submitList(items) {
            notifyDataSetChanged()
        }
    }

    /**
     * Take care of loading in widget preview image or app icon,
     * along with widget label.
     */
    class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        val binding = WidgetItemBinding.bind(itemView)

        @SuppressLint("SetTextI18n")
        fun parseInfo(info: WidgetListInfo, picasso: Picasso) {
            binding.widgetName.text = info.widgetName
            binding.widgetSize.text = "${info.providerInfo.minWidth}x${info.providerInfo.minHeight}"

            val img = binding.widgetImage

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

    /**
     * A request handler for Picasso to load in an app's icon by
     * package name
     */
    class AppIconRequestHandler(context: Context) : RequestHandler() {
        companion object {
            const val SCHEME = "package"
        }

        private val pm = context.packageManager

        override fun canHandleRequest(data: Request): Boolean {
            return (data.uri != null && data.uri.scheme == SCHEME)
        }

        override fun load(request: Request, networkPolicy: Int): Result {
            val pName = request.uri.schemeSpecificPart

            val img = pm.getApplicationIcon(pName).mutate().toBitmap()
                //Create a Bitmap copy since Android recycles the one from `toBitmap()`
                //if it's a BitmapDrawable
                .run { copy(config, false) }

            return Result(img, Picasso.LoadedFrom.DISK)
        }
    }

    /**
     * A request handler for Picasso to load in a resource Drawable
     * from a remote package.
     */
    class RemoteResourcesIconHandler(context: Context) : RequestHandler() {
        companion object {
            const val SCHEME = "remote_res_widget"
        }

        private val pm = context.packageManager

        override fun canHandleRequest(data: Request): Boolean {
            return (data.uri != null && data.uri.scheme == SCHEME)
        }

        override fun load(request: Request, networkPolicy: Int): Result {
            val pathSegments = request.uri.pathSegments

            val pName = request.uri.host
            val id = pathSegments[0].toInt()

            val img = pm.getResourcesForApplication(pName).run { ResourcesCompat.getDrawable(this, id, newTheme()) }!!
                //Create a Bitmap copy since Android recycles the one from `toBitmap()`
                //if it's a BitmapDrawable
                .mutate().toBitmap().run { copy(config, false) }

            return Result(img, Picasso.LoadedFrom.DISK)
        }
    }
}