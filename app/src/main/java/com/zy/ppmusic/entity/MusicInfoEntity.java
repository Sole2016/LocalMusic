package com.zy.ppmusic.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class MusicInfoEntity implements Serializable,Parcelable{
    private String name;
    private String path;
    private int length;

    public MusicInfoEntity(){

    }

    public MusicInfoEntity(String name, int length,String path) {
        this.name = name;
        this.length = length;
        this.path = path;
    }

    protected MusicInfoEntity(Parcel in) {
        name = in.readString();
        path = in.readString();
        length = in.readInt();
    }

    public static final Creator<MusicInfoEntity> CREATOR = new Creator<MusicInfoEntity>() {
        @Override
        public MusicInfoEntity createFromParcel(Parcel in) {
            return new MusicInfoEntity(in);
        }

        @Override
        public MusicInfoEntity[] newArray(int size) {
            return new MusicInfoEntity[size];
        }
    };

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(path);
        dest.writeInt(length);
    }

    @Override
    public String toString() {
        return "MusicInfoEntity{" +
                "name='" + name + '\'' +
                ", path='" + path + '\'' +
                ", length=" + length +
                '}';
    }
}
