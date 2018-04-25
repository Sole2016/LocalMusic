package com.zy.ppmusic.mvp.view

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProvider
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.*
import android.support.design.widget.BottomSheetDialog
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.view.ViewCompat
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.support.v7.view.menu.MenuBuilder
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.ListPopupWindow
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import com.zy.ppmusic.App
import com.zy.ppmusic.R
import com.zy.ppmusic.adapter.MediaHeadAdapter
import com.zy.ppmusic.adapter.MenuAdapter
import com.zy.ppmusic.adapter.PlayQueueAdapter
import com.zy.ppmusic.adapter.TimeClockAdapter
import com.zy.ppmusic.mvp.base.AbstractBaseMvpActivity
import com.zy.ppmusic.mvp.contract.IMediaActivityContract
import com.zy.ppmusic.mvp.model.HeadViewModel
import com.zy.ppmusic.mvp.presenter.MediaPresenterImpl
import com.zy.ppmusic.service.MediaService
import com.zy.ppmusic.utils.*
import com.zy.ppmusic.widget.*
import kotlinx.android.synthetic.main.activity_media_linear.*
import kotlinx.android.synthetic.main.dl_content_del_item.view.*
import java.lang.ref.WeakReference
import java.util.*

/**
 * 与MediaService两种通信方式：
 *      1.MediaController.transportControls.playFromMediaId(String, Bundle);//只发送消息（最好与播放器状态相关）
 *      2.SessionCompat.sendCommand(String,Bundle,ResultReceiver);//需要获取结果
 */
class MediaActivity : AbstractBaseMvpActivity<MediaPresenterImpl>(), IMediaActivityContract.IMediaActivityView {
    private var mMediaBrowser: MediaBrowserCompat? = null
    /**
     * 媒体控制器
     */
    private var mMediaController: MediaControllerCompat? = null
    /**
     * 播放列表与service同步
     */
    private var mPlayQueueList: MutableList<MediaSessionCompat.QueueItem>? = null
    /**
     * 播放列表的recycler
     */
    private var mQueueRecycler: RecyclerView? = null
    /**
     * 展示播放列表的dialog
     */
    private var mBottomQueueDialog: BottomSheetDialog? = null
    /**
     * 播放列表的适配器
     */
    private var mBottomQueueAdapter: PlayQueueAdapter? = null
    /**
     * 当前播放的媒体id
     */
    private var mCurrentMediaIdStr: String? = null
    /**
     * 结束位置
     */
    private var endPosition: Long = 0
    /**
     * 自增量
     */
    private var stepPosition: Long = 0
    /**
     * 起始位置
     */
    private var startPosition: Long = 0
    /**
     * 媒体播放进度处理
     */
    private var mResultReceive: MediaResultReceive? = null
    /**
     * 是否正在拖动进度条
     */
    private var mIsTrackingBar: Boolean? = false
    /**
     * 选择播放模式的Pop
     */
    private var mBottomLoopModePop: ListPopupWindow? = null
    /**
     * 倒计时选择的dialog
     */
    private var mTimeClockDialog: BottomSheetDialog? = null
    /**
     * 倒计时弹窗ContentView
     */
    private var mTimeContentView: View? = null
    /**
     * 倒计时Recycler的适配器
     */
    private var mTimeClockAdapter: TimeClockAdapter? = null
    /**
     * 倒计时选择弹窗Recycler
     */
    private var mTimeClockRecycler: RecyclerView? = null
    /**
     * 倒计时展示的自定义TextView
     */
    private var mBorderTextView: BorderTextView? = null
    /**
     * 播放列表弹窗的contentView
     */
    private var mBottomQueueContentView: View? = null
    /**
     * 显示播放列表数量
     */
    private var mQueueCountTv: TextView? = null
    /**
     * 歌曲图片适配器
     */
    private var mHeadAdapter: MediaHeadAdapter? = null
    /**
     * 加载框
     */
    private var mLoadingDialog: LoadingDialog? = null
    /**
     * 记录循环是否已经开始
     */
    private var isStarted = false


    /**
     * 接收媒体服务回传的信息，这里处理的是当前播放的位置和进度
     */
    class MediaResultReceive(activity: MediaActivity, handler: Handler) : ResultReceiver(handler) {

        private var mWeakView: WeakReference<MediaActivity>? = null

        init {
            this.mWeakView = WeakReference(activity)
        }

        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
            super.onReceiveResult(resultCode, resultData)
            if (mWeakView!!.get() == null) {
                return
            }
            val activity = mWeakView!!.get()
            when (resultCode) {
                MediaService.COMMAND_POSITION_CODE -> {
                    val position = resultData.getInt("position").toLong()
                    activity!!.startPosition = position
                    val percent = ((activity.startPosition * 100f) / activity.endPosition * 1.0f)
                    if (activity.mIsTrackingBar!!.not()) {
                        activity.control_display_progress.progress = percent.toInt()
                    }
                    activity.control_display_time_tv.text = DateUtil.getInstance().getTime(position)
                }
                MediaService.COMMAND_UPDATE_QUEUE_CODE -> {
                    if (activity!!.mMediaController?.queue != null &&
                            activity.mMediaController!!.queue.size > 0) {
                        if (activity.mBottomQueueAdapter != null) {
                            activity.mBottomQueueAdapter?.setData(activity.mMediaController?.queue)
                            activity.showMsg("更新播放列表")
                        }
                        activity.mPlayQueueList = activity.mMediaController?.queue
                    }
                }
                else -> {
                    PrintLog.print("MediaResultReceive other result....$resultCode," + resultData.toString())
                }
            }
        }

    }

    override fun getContentViewId(): Int = R.layout.activity_media_linear

    override fun createPresenter(): MediaPresenterImpl = MediaPresenterImpl(this)

    override fun initViews() {
        tb_media.setBackgroundColor(ContextCompat.getColor(this, R.color.colorTheme))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tb_media.elevation = 0f
        }

        setSupportActionBar(tb_media)

        //斜边View的背景
        val drawable = TimBackGroundDrawable()
        drawable.setDrawableColor(ContextCompat.getColor(this, R.color.colorTheme))
        drawable.setPercent(TimBackGroundDrawable.TOP)
        ViewCompat.setBackground(media_title_tint, drawable)
        //专辑图片的圆形背景
        val dp2px = UiUtils.dp2px(this, 110)
        val vpDrawable = RoundDrawable(dp2px, ContextCompat.getColor(this, R.color.colorGray))
        ViewCompat.setBackground(vp_show_media_head, vpDrawable)
        //设置底部的斜边
        val bottomBackGround = TimBackGroundDrawable()
        bottomBackGround.setDrawableColor(UiUtils.getColor(R.color.colorTheme))
        bottomBackGround.setCorner(TimBackGroundDrawable.LEFT)
        bottomBackGround.setPercent(TimBackGroundDrawable.BOTTOM)
        ViewCompat.setBackground(tv_show_position, bottomBackGround)

        control_display_progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    control_display_time_tv.text = DateUtil.getInstance().getTime(progress * stepPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                mIsTrackingBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                //初始化的时候点击的按钮直接播放当前的media
                val extra = Bundle()
                extra.putString(MediaService.ACTION_PARAM, MediaService.ACTION_SEEK_TO)
                extra.putInt(MediaService.SEEK_TO_POSITION_PARAM, (seekBar!!.progress * stepPosition).toInt())
                mPresenter!!.playWithId(mMediaController, mCurrentMediaIdStr, extra)
                mIsTrackingBar = false
            }
        })
        //循环模式点击监听
        control_action_loop_model.setOnClickListener({
            createLoopModePop()
        })
        //播放按钮监听
        control_action_play_pause.setOnClickListener({
            //初始化的时候点击的按钮直接播放当前的media
            val extra = Bundle()
            extra.putString(MediaService.ACTION_PARAM, MediaService.ACTION_PLAY_WITH_ID)
            if (mCurrentMediaIdStr != null) {
                mPresenter!!.playWithId(mMediaController, mCurrentMediaIdStr, extra)
            } else {
                PrintLog.print(getString(R.string.empty_play_queue))
                mPresenter!!.playWithId(mMediaController, "-1", extra)
            }
        })
        //播放列表监听
        control_action_show_queue.setOnClickListener({
            createBottomQueueDialog()
        })
    }


    /*专辑图片位置改变监听*/
    private val mHeadChangeListener = object : ViewPager.OnPageChangeListener {
        private var dragBeforeIndex = -1

        override fun onPageScrollStateChanged(state: Int) {
            if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                dragBeforeIndex = vp_show_media_head.currentItem
            }
        }

        override fun onPageScrolled(position: Int, positionOffset: Float,
                                    positionOffsetPixels: Int) {
            if (positionOffset == 0f && positionOffsetPixels == 0) {
                if (dragBeforeIndex == vp_show_media_head.currentItem) {
                    return
                }
                PrintLog.i("-----------ViewPager index 更新 ${vp_show_media_head.currentItem}")
                val currentMediaId = DataTransform.getInstance().mediaIdList[vp_show_media_head.currentItem]
                if (StringUtils.ifEquals(currentMediaId, mCurrentMediaIdStr)) {
                    return
                }
                mPresenter!!.skipToPosition(mMediaController, vp_show_media_head.currentItem.toLong())
                updateQueueSize(mPlayQueueList!!.size, vp_show_media_head.currentItem + 1)
            }
        }
        /**
         * 直接跳转到最后一个或者第一个只有这个
         */
        override fun onPageSelected(position: Int) {
            onPageScrolled(position,0f,0)
            println("dragIndex====$dragBeforeIndex but now position is $position")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        if (menu != null) {
            if (menu::class == MenuBuilder::class) {
                try {
                    val method = menu::class.java.getDeclaredMethod("setOptionalIconsVisible",
                            Boolean::class.java)
                    method.isAccessible = true
                    method.invoke(menu, true)
                } catch (e: Exception) {
                    PrintLog.d("反射显示图标失败")
                }
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.menu_media_scan -> {
                showMsg(getString(R.string.start_scanning_the_local_file))
                mPresenter?.refreshQueue(applicationContext, true)
            }
//            R.id.menu_blue_connect -> {
//                val intent = Intent(this, BlScanActivity::class.java)
//                startActivity(intent)
//            }
            R.id.menu_count_time -> {
                createTimeClockDialog()
            }
        }
        return true
    }

    /**
     * 创建播放列表
     */
    @SuppressLint("InflateParams")
    private fun createBottomQueueDialog() {
        mBottomQueueDialog = BottomSheetDialog(this)
        if (mBottomQueueAdapter == null) {
            mBottomQueueAdapter = PlayQueueAdapter()
            mBottomQueueAdapter?.setItemClickListener { v, index ->
                if (v.id == R.id.queue_item_del) {
                    createDelQueueItemDialog(index)
                    return@setItemClickListener
                }
                //点击播放列表直接播放选中的media
                mPresenter!!.skipToPosition(mMediaController, index.toLong())
                if (mBottomQueueDialog!!.isShowing) {
                    mBottomQueueDialog?.cancel()
                    mBottomQueueDialog?.dismiss()
                    mBottomQueueDialog = null
                    mBottomQueueAdapter = null
                }
            }

            mBottomQueueAdapter!!.setItemLongClickListener { _, position ->
                createQueueItemDetailDialog(position)
            }
        }
        if (mBottomQueueContentView == null) {
            mBottomQueueContentView = LayoutInflater.from(this).inflate(R.layout.play_queue_layout, null)
            mQueueRecycler = mBottomQueueContentView?.findViewById(R.id.control_queue_recycle)
            mQueueCountTv = mBottomQueueContentView?.findViewById(R.id.control_queue_count)
        } else {
            val parent = mBottomQueueContentView?.parent as ViewGroup
            parent.removeView(mBottomQueueContentView)
        }

        if (mCurrentMediaIdStr != null && mBottomQueueAdapter != null) {
            mBottomQueueAdapter?.selectIndex = DataTransform.getInstance().getMediaIndex(mCurrentMediaIdStr)
        }
        mQueueCountTv?.text = String.format(Locale.CHINA, getString(R.string.string_queue_playing_position),
                (mBottomQueueAdapter!!.selectIndex + 1), if (mPlayQueueList == null) 0 else mPlayQueueList?.size)
        mBottomQueueDialog?.setContentView(mBottomQueueContentView)
        mQueueRecycler?.adapter = mBottomQueueAdapter
        mQueueRecycler?.layoutManager = LinearLayoutManager(applicationContext)
        mQueueRecycler?.addItemDecoration(RecycleViewDecoration(this, LinearLayoutManager.VERTICAL,
                R.drawable.recyclerview_vertical_line, UiUtils.dp2px(this, 25)))
        mBottomQueueAdapter?.setData(mPlayQueueList)
        mBottomQueueDialog?.show()
    }

    /**
     * 显示列表item的详细信息
     */
    private fun createQueueItemDetailDialog(position: Int): Boolean {
        if (mPlayQueueList == null) {
            return false
        }

        if (position < 0 || position >= mPlayQueueList!!.size) {
            return false
        }

        val item = mPlayQueueList!![position].description ?: return false
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle(String.format(Locale.CHINA, getString(R.string.show_name_and_author), item.title, item.subtitle))
        dialog.setMessage(item.mediaUri.toString())
        dialog.create().show()
        return true
    }

    /**
     * 显示确认删除提示
     */
    @SuppressLint("InflateParams")
    private fun createDelQueueItemDialog(position: Int) {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle(getString(R.string.string_sure_del))
        val delContentView = LayoutInflater.from(this).inflate(R.layout.dl_content_del_item, null)
        delContentView.setPadding(UiUtils.dp2px(this, 20), UiUtils.dp2px(this, 20),
                UiUtils.dp2px(this, 10), UiUtils.dp2px(this, 10))
        dialog.setView(delContentView)
        dialog.setPositiveButton(getString(R.string.string_del)) { _, _ ->
            if (delContentView.checkbox_dl_content_message.isChecked) {
                mPresenter?.deleteFile(DataTransform.getInstance().getPath(position)) as Boolean
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.parse(DataTransform.getInstance().getPath(position)))
                sendBroadcast(intent)
            }
            mMediaController!!.removeQueueItem(mPlayQueueList!![position].description)
            mPlayQueueList?.removeAt(position)
            mBottomQueueAdapter?.setData(mPlayQueueList)
            mBottomQueueAdapter?.notifyItemRemoved(position)
            mQueueCountTv?.text = String.format(Locale.CHINA, getString(R.string.string_queue_playing_position),
                    (mBottomQueueAdapter!!.selectIndex + 1), mPlayQueueList?.size)
        }
        dialog.setNegativeButton(getString(R.string.string_cancel)) { d, _ ->
            d.cancel()
            d.dismiss()
        }
        dialog.create().show()
    }

    /**
     * 创建循环模式
     */
    private fun createLoopModePop() {
        if (mBottomLoopModePop == null) {
            mBottomLoopModePop = ListPopupWindow(this)
            mBottomLoopModePop?.anchorView = control_action_loop_model
            mBottomLoopModePop?.setDropDownGravity(Gravity.TOP and Gravity.END)
            mBottomLoopModePop?.setAdapter(MenuAdapter(this))
            mBottomLoopModePop?.setContentWidth(UiUtils.dp2px(this, 110))
            mBottomLoopModePop?.horizontalOffset = control_action_loop_model.measuredWidth
            mBottomLoopModePop?.setOnItemClickListener { _, _, position, _ ->
                setPlayMode(position)
                mBottomLoopModePop?.dismiss()
            }
        }
        mBottomLoopModePop?.show()
    }

    /**
     * 创建倒计时
     */
    @SuppressLint("InflateParams")
    private fun createTimeClockDialog() {
        mTimeClockDialog = BottomSheetDialog(this)
        mTimeContentView = LayoutInflater.from(this).inflate(R.layout.layout_time_lock, null)
        mTimeClockRecycler = mTimeContentView?.findViewById(R.id.time_selector_recycler)
        mTimeClockRecycler?.layoutManager = LinearLayoutManager(this)
        mTimeClockAdapter = TimeClockAdapter()
        mTimeClockAdapter?.setOnItemClickListener { _, position ->
            mTimeClockDialog?.cancel()
            if (getCurrentTimeClockLength(position) != 0) {
                //如果正在倒计时判断是否需要停止倒计时
                val bundle = Bundle()
                bundle.putLong(MediaService.ACTION_COUNT_DOWN_TIME, (getCurrentTimeClockLength(position) * 1000 * 60).toLong())
                mPresenter!!.sendCustomAction(mMediaController, MediaService.ACTION_COUNT_DOWN_TIME, bundle)
                mTimeClockDialog = null
            }
        }
        //如果正在倒计时显示取消计时选项，否则隐藏
        if (mBorderTextView != null && mBorderTextView?.visibility == View.VISIBLE) {
            mTimeClockAdapter?.setTicking(true)
        } else {
            mTimeClockAdapter?.setTicking(false)
        }
        mTimeClockRecycler?.adapter = mTimeClockAdapter

        val lengthSeekBar = mTimeContentView?.findViewById<SeekBar>(R.id.time_selector_seek_bar)
        val progressHintTv = mTimeContentView?.findViewById<TextView>(R.id.time_selector_progress_hint_tv)
        progressHintTv?.visibility = View.VISIBLE
        progressHintTv?.text = String.format(Locale.CHINA, "%d", (lengthSeekBar!!.progress + 1))
        lengthSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var percent = 0f
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                progressHintTv?.translationX = percent * progress
                progressHintTv?.text = String.format(Locale.CHINA, "%d", (progress + 1))
            }

            override fun onStartTrackingTouch(s: SeekBar?) {
                val transXRound = (s!!.measuredWidth - s.paddingLeft - s.paddingRight
                        + progressHintTv!!.measuredWidth / 2).toFloat()
                val mMaxProgress = s.max.toFloat()
                percent = transXRound / mMaxProgress
                progressHintTv.visibility = View.VISIBLE
            }

            override fun onStopTrackingTouch(s: SeekBar?) {
                mTimeClockRecycler?.postDelayed({
                    progressHintTv?.visibility = View.INVISIBLE
                }, 1000)
            }
        })
        val btnSure = mTimeContentView?.findViewById<View>(R.id.time_selector_sure_btn)
        btnSure?.setOnClickListener {
            val bundle = Bundle()
            bundle.putLong(MediaService.ACTION_COUNT_DOWN_TIME,
                    ((lengthSeekBar.progress + 1) * 1000 * 60).toLong())
            mPresenter!!.sendCustomAction(mMediaController, MediaService.ACTION_COUNT_DOWN_TIME, bundle)
            mTimeClockDialog?.cancel()
            mTimeClockDialog = null
        }
        mTimeClockDialog?.setContentView(mTimeContentView)
        mTimeClockDialog?.show()
    }

    /**
     * 获取当前倒计时长度
     */
    private fun getCurrentTimeClockLength(position: Int): Int =//如果处于倒计时状态，第一条为取消倒计时
            if (mTimeClockAdapter!!.isTick) {
                //发送停止倒计时，隐藏倒计时文本
                if (position == 0) {
                    mPresenter!!.sendCustomAction(mMediaController, MediaService.ACTION_STOP_COUNT_DOWN, null)
                    mBorderTextView?.hide()
                    0
                } else {
                    mTimeClockAdapter!!.getItem(position - 1)
                }
            } else {
                mTimeClockAdapter!!.getItem(position)
            }

    override fun checkConnection() {
        if (mMediaBrowser?.isConnected!!.not()) {
            mMediaBrowser?.connect()
        }
    }

    /**
     * IView
     * 刷新列表的回调
     */
    override fun refreshQueue(mPathList: java.util.ArrayList<String>?, isRefresh: Boolean) {
        if (mMediaController == null)
            return
        if (mPathList != null && mPathList.size > 0) {
            showMsg(String.format(Locale.CHINA, getString(R.string.format_string_search_media_count), mPathList.size))
            mPresenter!!.sendCommand(mMediaController, MediaService.COMMAND_UPDATE_QUEUE, null, mResultReceive)
        } else {
            showMsg(getString(R.string.no_media_searched))
            mPresenter!!.playWithId(mMediaController, "-1", null)
        }
    }

    private fun updateHead() = if (mHeadAdapter == null) {
        mHeadAdapter = MediaHeadAdapter(supportFragmentManager, DataTransform.getInstance().pathList)
        vp_show_media_head.offscreenPageLimit = 1
        vp_show_media_head.addOnPageChangeListener(mHeadChangeListener)
        vp_show_media_head.adapter = mHeadAdapter
    } else {
        mHeadAdapter!!.setPathList(DataTransform.getInstance().pathList)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        //清除状态缓存，避免出现异常，界面刷新由onStart方法中完成
        //猜测是由于fragment数据大小超出限制
//        outState?.clear()
        super.onSaveInstanceState(Bundle())
        println("onSaveInstanceState............................." + outState?.toString())
    }

    /**
     * 首次加载完成
     */
    override fun loadFinished() {
        if (mMediaBrowser == null) {
            val serviceComponentName = ComponentName(this, MediaService::class.java)
            mMediaBrowser = MediaBrowserCompat(this, serviceComponentName, mConnectionCallBack, null)
        }
        if (mMediaBrowser!!.isConnected) {
            mMediaBrowser?.disconnect()
        }
        mMediaBrowser?.connect()
    }

    override fun onStart() {
        super.onStart()
        //延迟加载数据
        vp_show_media_head.postDelayed({
            mPresenter?.refreshQueue(this, false)
        }, 100)
    }

    private var mLoadingDialogFragment:LoadingDialogFragment ?= null

    /**
     * 显示加载框
     */
    override fun showLoading() {
        PrintLog.d("showLoading")
//        if (mLoadingDialog == null) {
//            mLoadingDialog = LoadingDialog(this)
//        }
//        mLoadingDialog?.setCancelable(false)
//        mLoadingDialog!!.show()
        if(mLoadingDialogFragment == null){
            mLoadingDialogFragment = LoadingDialogFragment()
        }
        mLoadingDialogFragment?.isCancelable = false
        mLoadingDialogFragment?.show(supportFragmentManager,"loading")
    }

    /**
     * 隐藏加载框
     */
    override fun hideLoading() {
        PrintLog.d("hideLoading")
        if (mLoadingDialogFragment != null && mLoadingDialogFragment!!.isVisible) {
            mLoadingDialogFragment!!.dismiss()
        }
    }

    /**
     * 连接状态回调
     */
    private val mConnectionCallBack = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            super.onConnected()
            PrintLog.print("connected service....")
            //mMediaBrowser!!.root 对应service的BrowserRoot 可以是包名
            mMediaBrowser?.subscribe(mMediaBrowser!!.root, subscriptionCallBack)
            mMediaController = MediaControllerCompat(this@MediaActivity, mMediaBrowser!!.sessionToken)
            mResultReceive = MediaResultReceive(this@MediaActivity, Handler())
            MediaControllerCompat.setMediaController(this@MediaActivity, mMediaController)
            mMediaController?.registerCallback(mControllerCallBack)
            loadMode()
        }

        override fun onConnectionSuspended() {
            super.onConnectionSuspended()
            PrintLog.print("onConnectionSuspended....")
            mMediaBrowser?.unsubscribe(mMediaBrowser!!.root, subscriptionCallBack)
            if (mMediaController != null) {
                mMediaController!!.unregisterCallback(mControllerCallBack)
                MediaControllerCompat.setMediaController(this@MediaActivity, null)
            }
        }

        override fun onConnectionFailed() {
            super.onConnectionFailed()
            PrintLog.print("onConnectionFailed.....")
        }
    }

    /**
     * 加载本地缓存的播放模式
     */
    private fun loadMode() {
        if (mPresenter != null && mMediaController != null) {
            setPlayMode(mPresenter?.getLocalMode(applicationContext)!!)
        }
    }

    /**
     * 设置播放模式
     */
    private fun setPlayMode(mode: Int) {
        when (mode) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                control_action_loop_model.setImageResource(R.drawable.ic_loop_model_normal)
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> {
                control_action_loop_model.setImageResource(R.drawable.ic_loop_model_only)
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                control_action_loop_model.setImageResource(R.drawable.ic_loop_mode_list)
            }
        }
        mPresenter?.setRepeatMode(this, mMediaController, mode)
    }

    /**
     * 播放器相关回调
     */
    private val subscriptionCallBack = object : MediaBrowserCompat.SubscriptionCallback() {
        //service加载完成列表回调
        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
            PrintLog.print("onChildrenLoaded.....activity... size " + children.size)
            if (children.size > 0) {
                if (mHeadAdapter == null) {
                    updateHead()
                }
                //如果当前在播放状态
                if (mMediaController?.playbackState?.state != PlaybackStateCompat.STATE_NONE) {
                    val metadata = mMediaController?.metadata
                    //请求获取当前播放位置
                    if (metadata != null) {
                        mCurrentMediaIdStr = metadata.description.mediaId
                        val displayTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                        val subTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                        endPosition = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                        stepPosition = endPosition / 100L
                        if (startPosition != 0L) {
                            val percent = startPosition * 100f / endPosition * 1.0f
                            control_display_progress.progress = percent.toInt()
                        }
                        control_display_duration_tv.text = DateUtil.getInstance().getTime(endPosition)
                        setMediaInfo(StringUtils.ifEmpty(displayTitle, UiUtils.getString(R.string.unknown_name))
                                , StringUtils.ifEmpty(subTitle, UiUtils.getString(R.string.unknown_author)))
                        val position = DataTransform.getInstance().getMediaIndex(mCurrentMediaIdStr)
                        vp_show_media_head.setCurrentItem(position, false)
                        updateQueueSize(mHeadAdapter!!.count, position + 1)
                    }
                    handlePlayState(mMediaController!!.playbackState!!.state)
                } else {
                    val subTitle = StringUtils.ifEmpty("${children[0].description.subtitle}",
                            UiUtils.getString(R.string.unknown_author))
                    val title = StringUtils.ifEmpty("${children[0].description.title}",
                            UiUtils.getString(R.string.unknown_name))
                    setMediaInfo(title, subTitle)
                    mCurrentMediaIdStr = children[0].description.mediaId
                    val position = DataTransform.getInstance().getMediaIndex(mCurrentMediaIdStr)
                    updateQueueSize(DataTransform.getInstance().mediaIdList.size, position + 1)
                    val extra = Bundle()
                    extra.putString(MediaService.ACTION_PARAM, MediaService.ACTION_PLAY_INIT)
                    mPresenter!!.playWithId(mMediaController, mCurrentMediaIdStr, extra)
                }
            } else {
                control_display_time_tv.text = getString(R.string.string_time_init)
                control_display_duration_tv.text = getString(R.string.string_time_init)
                setMediaInfo(getString(R.string.app_name), getString(R.string.app_name))
            }
            hideLoading()
        }

        //播放列表加载失败
        override fun onError(parentId: String) =
                PrintLog.print("SubscriptionCallback onError called.....")
    }

    /**
     * 媒体控制
     */
    private val mControllerCallBack = object : MediaControllerCompat.Callback() {
        //当前歌曲信息变化回调
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            PrintLog.print("onMetadataChanged ------------- called")
            updateMetadata(metadata)
        }

        fun updateMetadata(metadata: MediaMetadataCompat?) {
            if (metadata != null) {
                mCurrentMediaIdStr = metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                val displayTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                val subTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
                endPosition = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                stepPosition = endPosition / 100L
                if (startPosition != 0L) {
                    val percent = ((startPosition * 1.0f) / endPosition * 1.0f)
                    control_display_progress.progress = (percent * 100f).toInt()
                }
                control_display_duration_tv.text = DateUtil.getInstance().getTime(endPosition)
                PrintLog.print("endPosition=$endPosition,step=$stepPosition")
                val position = DataTransform.getInstance().getMediaIndex(mCurrentMediaIdStr)
                //只在dialog显示时刷新
                if (mCurrentMediaIdStr != null && mBottomQueueDialog != null) {
                    if (mBottomQueueDialog!!.isShowing) {
                        mBottomQueueAdapter?.selectIndex = position
                        mBottomQueueAdapter?.notifyDataSetChanged()
                    }
                }
                setMediaInfo(StringUtils.ifEmpty(displayTitle, getString(R.string.unknown_name)),
                        StringUtils.ifEmpty(subTitle, getString(R.string.unknown_author)))
                if (mHeadAdapter != null && mHeadAdapter!!.count > position) {
                    vp_show_media_head.setCurrentItem(position, false)
                }
                updateQueueSize(DataTransform.getInstance().pathList.size, position + 1)
            }
        }

        //播放列表变化回调
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            super.onQueueChanged(queue)
            if (queue?.size == 0) {
                setMediaInfo(getString(R.string.app_name), getString(R.string.app_name))
                return
            }
            if (mBottomQueueAdapter != null) {
                mBottomQueueAdapter?.setData(queue)
                showMsg("更新播放列表")
            }
            mPlayQueueList = queue
            updateHead()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            PrintLog.print("onRepeatModeChanged.....")
        }

        //播放器状态改变回调
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            PrintLog.print("onPlaybackStateChanged....." + state?.state)
            PrintLog.print("position=" + state?.position + ",buffer=" + state?.bufferedPosition)
            PrintLog.print("endPosition=$endPosition")
            startPosition = state!!.position
            if (endPosition != 0L) {
                val percent = ((startPosition * 1.0f) / endPosition * 1.0f)
                control_display_progress.progress = (percent * 100f).toInt()
            }
            control_display_time_tv.text = DateUtil.getInstance().getTime(startPosition)
            handlePlayState(state.state)
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {
            super.onSessionEvent(event, extras)
            when (event) {
                MediaService.LOCAL_CACHE_POSITION_EVENT -> {
                    val lastPosition = extras?.getInt(MediaService.LOCAL_CACHE_POSITION_EVENT) as Int
                    if (endPosition != 0L) {
                        control_display_progress.progress = (lastPosition * 100 / endPosition).toInt()
                        PrintLog.d("得到的缓存position=" + (lastPosition * 100 / endPosition).toInt())
                    }
                }
                MediaService.ERROR_PLAY_QUEUE_EVENT -> {
                    showMsg(getString(R.string.empty_play_queue))
                }
                MediaService.LOADING_QUEUE_EVENT -> {
                    showMsg(getString(R.string.queue_loading))
                    showLoading()
                }
                MediaService.LOAD_COMPLETE_EVENT -> {
                    showMsg(getString(R.string.loading_complete))
                    hideLoading()
                }
                MediaService.ACTION_COUNT_DOWN_TIME -> {
                    val mis = extras!!.getLong(MediaService.ACTION_COUNT_DOWN_TIME)
                    if (mBorderTextView == null) {
                        mBorderTextView = BorderTextView(this@MediaActivity)
                    }
                    mBorderTextView?.show(vp_show_media_head, DateUtil.getInstance().getTime(mis))
                }
                MediaService.ACTION_COUNT_DOWN_END -> {
                    if (mBorderTextView != null) {
                        mBorderTextView?.hide()
                        disConnectService()
                        finish()
                    }
                }
                MediaService.UPDATE_POSITION_EVENT -> {
                    val position = extras!!.getInt(MediaService.UPDATE_POSITION_EVENT, 0)
                    startPosition = position.toLong()
                    if (endPosition != 0L) {
                        val percent = ((startPosition * 1.0f) / endPosition * 1.0f)
                        control_display_progress.progress = (percent * 100f).toInt()
                    }
                    control_display_time_tv.text = DateUtil.getInstance().getTime(startPosition)
                }
                else -> {
                    PrintLog.print("onSessionEvent....." + event + "," + extras.toString())
                }
            }
        }
    }

    private fun updateQueueSize(total: Int, current: Int) {
        if (total == 0) {
            tv_show_position.visibility = View.GONE
            return
        }
        tv_show_position.visibility = View.VISIBLE
        tv_show_position.text = String.format(Locale.CHINA, "%2d/%2d", current, total)
    }

    private var mPlayStateModel: HeadViewModel? = null

    private fun createModelIfNeed() {
        if (mPlayStateModel == null) {
            val provider = ViewModelProvider(this,
                    ViewModelProvider.AndroidViewModelFactory(App.getInstance()))
            mPlayStateModel = provider.get(HeadViewModel::class.java)
        }
    }

    /**
     * 播放器状态处理
     */
    fun handlePlayState(state: Int) {
        PrintLog.print("handlePlayState=$state")
        createModelIfNeed()
        if (state != PlaybackStateCompat.STATE_PLAYING) {
            stopLoop()
            mPlayStateModel!!.setPlayState(false)
            control_action_play_pause.setImageResource(R.drawable.ic_black_play)
            if (state == PlaybackStateCompat.STATE_STOPPED) {
                control_display_progress.progress = 0
            }
        } else {
            startLoop()
            mPlayStateModel!!.setPlayState(true)
            control_action_play_pause.setImageResource(R.drawable.ic_black_pause)
        }
    }

    private fun startLoop() {
        if (!isStarted) {
            mMediaController?.sendCommand(MediaService.COMMAND_START_LOOP, null, null)
            isStarted = true
        }
    }

    private fun stopLoop() {
        if (isStarted) {
            mMediaController?.sendCommand(MediaService.COMMAND_STOP_LOOP, null, null)
            isStarted = false
        }
    }

    private fun showMsg(msg: String) {
        EasyTintView.makeText(vp_show_media_head, msg, EasyTintView.TINT_SHORT).show()
    }

    /**
     * 更新媒体信息
     */
    private fun setMediaInfo(displayTitle: String?, subTitle: String?) {
        supportActionBar?.title = displayTitle
        supportActionBar?.subtitle = subTitle
    }

    /**
     * 断开媒体服务
     */
    private fun disConnectService() {
        //释放控制器
        if (mMediaController != null) {
            mMediaController!!.unregisterCallback(mControllerCallBack)
            mMediaController = null
            MediaControllerCompat.setMediaController(this, null)
        }
        //停止进度更新
        stopLoop()
        //取消播放状态监听
        if (mMediaBrowser != null) {
            if (mMediaBrowser?.root != null) {
                mMediaBrowser?.unsubscribe(mMediaBrowser!!.root, subscriptionCallBack)
            }
            //断开与媒体服务的链接
            mMediaBrowser?.disconnect()
            mMediaBrowser = null
        }
    }

    override fun onStop() {
        super.onStop()
        PrintLog.print("onStop called")
        //正常按键返回退出调用
        if (isFinishing) {
            disConnectService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //配置发生变化获取其他意外终止
        disConnectService()
        PrintLog.print("MediaActivity is destroy")
        ViewCompat.setBackground(media_title_tint, null)
        ViewCompat.setBackground(vp_show_media_head, null)
        ViewCompat.setBackground(tv_show_position, null)
        if (mLoadingDialog != null) {
            mLoadingDialog!!.cancel()
            mLoadingDialog = null
        }
        //释放播放列表弹窗
        if (mBottomQueueDialog != null) {
            if (mBottomQueueDialog!!.isShowing) {
                mBottomQueueDialog!!.dismiss()
            }
            mQueueCountTv = null
            mBottomQueueContentView = null
            mBottomQueueDialog = null
            mBottomQueueAdapter = null
        }
        //释放加载中弹窗
        findViewById<FrameLayout>(android.R.id.content).removeAllViews()
        //释放循环模式弹窗
        if (mBottomLoopModePop != null) {
            if (mBottomLoopModePop!!.isShowing) {
                mBottomLoopModePop!!.dismiss()
            }
            mBottomLoopModePop = null
        }
        //释放时间计时弹窗
        if (mTimeClockDialog != null) {
            if (mTimeClockDialog!!.isShowing) {
                mTimeClockDialog!!.dismiss()
            }
            mTimeContentView = null
            mTimeClockDialog = null
            mTimeClockAdapter = null
        }
        mResultReceive = null
        //去除ViewPager的监听
        vp_show_media_head.removeOnPageChangeListener(mHeadChangeListener)
        //去除SeekBar的监听
        control_display_progress.setOnSeekBarChangeListener(null)

        control_action_show_queue.setOnClickListener(null)
        control_action_loop_model.setOnClickListener(null)
        control_action_play_pause.setOnClickListener(null)
    }
}
