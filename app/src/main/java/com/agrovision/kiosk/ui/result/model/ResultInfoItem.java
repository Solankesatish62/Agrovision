package com.agrovision.kiosk.ui.result.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * ResultInfoItem
 *
 * Represents a single piece of medical guidance.
 */
public final class ResultInfoItem implements Parcelable {

    public enum Type implements Serializable {
        CROP, PEST, USAGE, TIMING, DOSAGE, CAUTION
    }

    public final Type type;
    public final String text;

    public ResultInfoItem(Type type, String text) {
        this.type = type;
        this.text = text;
    }

    protected ResultInfoItem(Parcel in) {
        type = (Type) in.readSerializable();
        text = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(type);
        dest.writeString(text);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ResultInfoItem> CREATOR = new Creator<ResultInfoItem>() {
        @Override
        public ResultInfoItem createFromParcel(Parcel in) {
            return new ResultInfoItem(in);
        }

        @Override
        public ResultInfoItem[] newArray(int size) {
            return new ResultInfoItem[size];
        }
    };
}
