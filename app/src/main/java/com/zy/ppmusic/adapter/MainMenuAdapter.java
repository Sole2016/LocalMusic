package com.zy.ppmusic.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.zy.ppmusic.R;
import com.zy.ppmusic.entity.MainMenuEntity;

import java.lang.ref.WeakReference;
import java.util.List;
/**
 * @author ZY
 */
public class MainMenuAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = "MainMenuAdapter";
    private List<MainMenuEntity> mDataList;
    private OnRecycleItemClickListener listener;
    private Context mContext;

    public MainMenuAdapter(List<MainMenuEntity> dataList) {
        this.mDataList = dataList;
    }

    public void setListener(OnRecycleItemClickListener listener) {
        this.listener = listener;
    }

    public void onUpdateItem(int position, String title, int resId){
        MainMenuEntity itemByTitle = getItemByPosition(position);
        if(itemByTitle != null){
            if(resId != -1){
                itemByTitle.setMenuRes(resId);
            }
            if(!TextUtils.isEmpty(title)){
                itemByTitle.setMenuTitle(title);
            }
            notifyItemChanged(position);
        }
    }

    public MainMenuEntity getItemByPosition(int pos){
        return mDataList.get(pos);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        mContext = viewGroup.getContext();
        return new MenuHolder(LayoutInflater.from(mContext).inflate(
                R.layout.item_main_menu, viewGroup, false),listener);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        MenuHolder menuHolder = (MenuHolder) viewHolder;
        Drawable topDrawable = ContextCompat.getDrawable(mContext, mDataList.get(i).getMenuRes());
        menuHolder.menuTitle.setCompoundDrawablesWithIntrinsicBounds(null,topDrawable,null,null);
        menuHolder.menuTitle.setText(mDataList.get(i).getMenuTitle());
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 : mDataList.size();
    }

    private static class MenuHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView menuTitle;
        private WeakReference<OnRecycleItemClickListener> weakReference;

        private MenuHolder(View itemView,OnRecycleItemClickListener l) {
            super(itemView);
            if(l != null){
                weakReference = new WeakReference<>(l);
            }
            menuTitle = itemView.findViewById(R.id.main_menu_title);
            menuTitle.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if(weakReference != null){
                weakReference.get().onItemClick(itemView,getAdapterPosition());
            }
        }
    }

    public interface OnRecycleItemClickListener {
        /**
         * item点击回调
         * @param view itemView
         * @param position itemPosition
         */
        void onItemClick(View view, int position);
    }
}
