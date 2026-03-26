package com.agrovision.kiosk.ui.result.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * ScanResult
 *
 * FINAL immutable output of the recognition pipeline
 * for a single medicine bottle.
 */
public final class ScanResult implements Parcelable {

    public final ResultType resultType;

    // KNOWN only
    public final String medicineId;
    public final String displayName;
    public final List<String> imageUrls;
    public final List<ResultInfoItem> infoItems;

    // UNKNOWN / diagnostics
    public final String rawOcrText;
    public final boolean isConfidenceLow;

    public ScanResult(
            ResultType resultType,
            String medicineId,
            String displayName,
            List<String> imageUrls,
            List<ResultInfoItem> infoItems,
            String rawOcrText,
            boolean isConfidenceLow
    ) {
        this.resultType = resultType;
        this.medicineId = medicineId;
        this.displayName = displayName;
        this.imageUrls = imageUrls != null ? imageUrls : new ArrayList<>();
        this.infoItems = infoItems;
        this.rawOcrText = rawOcrText;
        this.isConfidenceLow = isConfidenceLow;
    }

    protected ScanResult(Parcel in) {
        resultType = (ResultType) in.readSerializable();
        medicineId = in.readString();
        displayName = in.readString();
        
        imageUrls = new ArrayList<>();
        in.readStringList(imageUrls);
        
        infoItems = in.createTypedArrayList(ResultInfoItem.CREATOR);
        
        rawOcrText = in.readString();
        isConfidenceLow = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(resultType);
        dest.writeString(medicineId);
        dest.writeString(displayName);
        dest.writeStringList(imageUrls);
        dest.writeTypedList(infoItems);
        dest.writeString(rawOcrText);
        dest.writeByte((byte) (isConfidenceLow ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ScanResult> CREATOR = new Creator<ScanResult>() {
        @Override
        public ScanResult createFromParcel(Parcel in) {
            return new ScanResult(in);
        }

        @Override
        public ScanResult[] newArray(int size) {
            return new ScanResult[size];
        }
    };
}
