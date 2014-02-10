package org.telegram.android.views;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextPaint;
import android.util.AttributeSet;
import com.extradea.framework.images.ImageReceiver;
import com.extradea.framework.images.tasks.*;
import org.telegram.android.R;
import org.telegram.android.core.background.MediaSender;
import org.telegram.android.core.background.SenderListener;
import org.telegram.android.core.model.*;
import org.telegram.android.core.model.media.*;
import org.telegram.android.core.wireframes.MessageWireframe;
import org.telegram.android.log.Logger;
import org.telegram.android.media.*;
import org.telegram.android.preview.MediaReceiver;
import org.telegram.android.ui.*;
import org.telegram.android.util.IOUtils;
import org.telegram.api.TLFileLocation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Author: Korshakov Stepan
 * Created: 15.08.13 1:14
 */
public class MessageMediaView extends BaseMsgView implements MediaReceiver {

    private static Movie lastMovie;
    private static long lastMovieStart;
    private static int lastDatabaseId;
    private static boolean moviePaused;
    private static Executor movieLoader = Executors.newSingleThreadExecutor();

    private static final String TAG = "MessageMediaView";

    public static String buildMapUrl(double latitude, double longitude, int width, int height) {
        String url = "https://maps.googleapis.com/maps/api/staticmap?center={center}&zoom=12&size=" + width + "x" + height + "&sensor=false";
        url = url.replace("{center}", latitude + "," + longitude);
        return url;
    }

    private Drawable stateSent;
    private Drawable stateHalfCheck;
    private Drawable stateFailure;
    private Drawable videoIcon;
    private Drawable mapPoint;
    private Drawable unsupportedMark;
    private TextPaint videoDurationPaint;
    private TextPaint downloadPaint;
    private TextPaint timePaint;
    private Paint bitmapPaint;
    private Paint bitmapFilteredPaint;
    private Paint timeBgRect;

    private Paint downloadBgRect;
    private Paint downloadBgLightRect;

    private Paint placeholderPaint;
    private Paint clockIconPaint;

    private Path path = new Path();
    private RectF rectF = new RectF();
    private Rect rect1 = new Rect();
    private Rect rect2 = new Rect();

    private int databaseId = -1;
    private String key;
    private int state;
    private int prevState;
    private long stateChangeTime;

    private ImageReceiver receiver;
    private ImageTask previewTask;
    private long previewAppearTime;

    private Bitmap oldPreview;
    private String oldPreviewKey;
    private int oldPreviewRegionW;
    private int oldPreviewRegionH;

    private Bitmap preview;
    private String previewKey;
    private int previewRegionW;
    private int previewRegionH;
    // private Bitmap previewCached;
    // private int fastPreviewHeight;
    // private int fastPreviewWidth;
    // private int previewHeight;
    // private int previewWidth;
    private boolean isOut;
    private boolean isVideo;
    private boolean isDownloadable;
    private boolean isUploadable;
    private boolean showMapPoint;
    private boolean isUnsupported;
    private int downloadProgress;
    private int oldDownloadProgress;
    private long downloadStateTime;
    private String downloadString;
    private String duration;
    private String date;

    private boolean isBindSizeCalled;
    private int desiredHeight;
    private int desiredWidth;
    private int timeWidth;

    private DownloadListener downloadListener;
    private SenderListener senderListener;

    private boolean isAnimatedProgress = true;

    private boolean scaleUpMedia = false;

    public MessageMediaView(Context context) {
        super(context);
        init();
    }

    public MessageMediaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MessageMediaView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    protected void init() {
        super.init();
        videoIcon = getResources().getDrawable(R.drawable.st_bubble_ic_video);
        mapPoint = getResources().getDrawable(R.drawable.st_map_pin);
        unsupportedMark = getResources().getDrawable(R.drawable.st_bubble_unknown);

        if (FontController.USE_SUBPIXEL) {
            videoDurationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            videoDurationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        videoDurationPaint.setTextSize(getSp(15));
        videoDurationPaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        videoDurationPaint.setColor(0xE6FFFFFF);

        if (FontController.USE_SUBPIXEL) {
            downloadPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            downloadPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        downloadPaint.setTextSize(getSp(15));
        downloadPaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        downloadPaint.setColor(0xE6FFFFFF);

        if (FontController.USE_SUBPIXEL) {
            timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        } else {
            timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        }
        timePaint.setTextSize(getSp(13));
        timePaint.setTypeface(FontController.loadTypeface(getContext(), "regular"));
        timePaint.setColor(0xB6FFFFFF);

        bitmapPaint = new Paint(/*Paint.ANTI_ALIAS_FLAG*/);
        //bitmapPaint.setAntiAlias(true);
        //bitmapPaint.setFilterBitmap(true);
        //bitmapPaint.setDither(true);

        bitmapFilteredPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        bitmapFilteredPaint.setAntiAlias(true);
        bitmapFilteredPaint.setFilterBitmap(true);
        bitmapFilteredPaint.setDither(true);

        timeBgRect = new Paint();
        timeBgRect.setColor(0xb6000000);

        downloadBgRect = new Paint();
        downloadBgRect.setColor(0xB6000000);
        downloadBgRect.setStyle(Paint.Style.FILL);
        downloadBgRect.setAntiAlias(true);

        downloadBgLightRect = new Paint();
        downloadBgLightRect.setColor(0x60ffffff);
        downloadBgLightRect.setStyle(Paint.Style.FILL);
        downloadBgLightRect.setAntiAlias(true);

        stateSent = getResources().getDrawable(R.drawable.st_bubble_ic_check_photo);
        stateHalfCheck = getResources().getDrawable(R.drawable.st_bubble_ic_halfcheck_photo);
        stateFailure = getResources().getDrawable(R.drawable.st_bubble_ic_warning);

        placeholderPaint = new Paint();
        placeholderPaint.setColor(Color.WHITE);

        clockIconPaint = new Paint();
        clockIconPaint.setStyle(Paint.Style.STROKE);
        clockIconPaint.setColor(Color.WHITE);
        clockIconPaint.setStrokeWidth(getPx(1));
        clockIconPaint.setAntiAlias(true);
        clockIconPaint.setFlags(Paint.ANTI_ALIAS_FLAG);


        downloadListener = new DownloadListener() {
            long lastNotify = 0;

            @Override
            public void onStateChanged(final String _key, final DownloadState state, final int percent) {
                if (!_key.equals(key))
                    return;

                if (lastNotify > 0) {
                    Logger.d(TAG, "Prev notify in " + (lastNotify - SystemClock.uptimeMillis()) + " ms");
                }
                lastNotify = SystemClock.uptimeMillis();

                if (downloadProgress != percent) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = percent;
                    downloadStateTime = SystemClock.uptimeMillis();
                }
                switch (state) {
                    case CANCELLED:
                        downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                        break;
                    case FAILURE:
                        downloadString = getResources().getString(R.string.st_bubble_media_try_again);
                        break;
                    case NONE:
                        downloadString = getResources().getString(R.string.st_bubble_media_download);
                        break;
                    case IN_PROGRESS:
                    case PENDING:
                        downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                        break;
                    case COMPLETED:
                        downloadString = null;
                        rebind();
                        break;
                }
                postInvalidate();
            }
        };
        application.getDownloadManager().registerListener(downloadListener);

        senderListener = new SenderListener() {
            @Override
            public void onUploadStateChanged(int localId, MediaSender.SendState state) {
                if (databaseId != localId)
                    return;

                if (downloadProgress != state.getUploadProgress()) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = state.getUploadProgress();
                    downloadStateTime = SystemClock.uptimeMillis();
                }

                if (state.isCanceled()) {
                    downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                } else if (state.isUploaded()) {
                    downloadString = null;
                } else if (state.isSent()) {
                    downloadString = null;
                    //rebind();
                } else {
                    downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                }
                postInvalidate();
            }
        };
        application.getMediaSender().registerListener(senderListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unbindOldPreview();
        unbindPreview();
        postInvalidate();
    }

    @Override
    public void unbind() {
        super.unbind();
        unbindOldPreview();
        unbindPreview();
    }

    private int[] buildSize(int w, int h) {
        float maxWidth = getPx(160);
        float maxHeight = getPx(300);

        float scale = Math.min(maxWidth / w, maxHeight / h);

        int scaledW = (int) (w * scale);
        int scaledH = (int) (h * scale);

        return new int[]{scaledW, scaledH};
    }

    private void bindSize(int w, int h) {
        int[] sizes = buildSize(w, h);
        desiredWidth = sizes[0];
        desiredHeight = sizes[1];
        isBindSizeCalled = true;
    }

    private void unbindPreview() {
        if (preview != null) {
            application.getUiKernel().getMediaLoader().getImageCache().decReference(previewKey);
            previewKey = null;
            preview = null;
            previewRegionH = 0;
            previewRegionW = 0;
        }
    }

    private void unbindOldPreview() {
        if (oldPreview != null) {
            application.getUiKernel().getMediaLoader().getImageCache().decReference(oldPreviewKey);
            oldPreviewKey = null;
            oldPreview = null;
            oldPreviewRegionH = 0;
            oldPreviewRegionW = 0;
        }
    }

    private boolean bindPreview(ImageTask task) {
//        if (task != null) {
//            previewTask = task;
//            receiver.receiveImage(previewTask);
//            preview = task.getResult();
//            if (preview != null) {
//                previewAppearTime = 0;
//                return true;
//            }
//        } else {
//            previewTask = null;
//            receiver.receiveImage(null);
//        }
        return false;
    }

    private void bindMedia(MessageWireframe message) {
        this.isBindSizeCalled = false;
        this.preview = null;
        this.isVideo = false;
        this.isUploadable = false;
        this.isDownloadable = false;
        this.downloadString = null;
        this.showMapPoint = false;
        this.key = null;
        this.isUnsupported = false;

        if (message.message.getExtras() instanceof TLLocalPhoto) {
            TLLocalPhoto mediaPhoto = (TLLocalPhoto) message.message.getExtras();
            bindSize(mediaPhoto.getFullW(), mediaPhoto.getFullH());

            boolean isBinded = false;
            if (!(mediaPhoto.getFullLocation() instanceof TLLocalFileEmpty)) {
                key = DownloadManager.getPhotoKey(mediaPhoto);
                if (application.getDownloadManager().getState(key) == DownloadState.COMPLETED) {
//                    int[] sizes = buildSize(mediaPhoto.getFullW(), mediaPhoto.getFullH());
//                    ScaleTask task = new ScaleTask(new FileSystemImageTask(application.getDownloadManager().getFileName(key)), sizes[0], sizes[1]);
//                    task.setPutInDiskCache(true);
//                    isBinded = bindPreview(task);

                    application.getUiKernel().getMediaLoader().requestFullLoading(mediaPhoto, application.getDownloadManager().getFileName(key), this);
                    isBinded = true;
                }
                isDownloadable = true;
            } else {
                isDownloadable = false;
            }

            if (!isBinded && mediaPhoto.hasFastPreview()) {
                isBinded = true;
                application.getUiKernel().getMediaLoader().requestFastLoading(mediaPhoto, this);
            }

            if (!isBinded) {
                application.getUiKernel().getMediaLoader().cancelRequest(this);
            }
        } else if (message.message.getExtras() instanceof TLLocalVideo) {
            TLLocalVideo mediaVideo = (TLLocalVideo) message.message.getExtras();
            bindSize(mediaVideo.getPreviewW(), mediaVideo.getPreviewH());

            isVideo = true;
            duration = TextUtil.formatDuration(mediaVideo.getDuration());
            placeholderPaint.setColor(Color.BLACK);

            if (!(mediaVideo.getVideoLocation() instanceof TLLocalFileEmpty)) {
                key = DownloadManager.getVideoKey(mediaVideo);
                isDownloadable = true;

                if (application.getDownloadManager().getState(key) == DownloadState.COMPLETED) {
//                    float maxWidth = getPx(160);
//                    float maxHeight = getPx(300);
//
//                    float scale = maxWidth / mediaVideo.getPreviewW();
//
//                    if (previewHeight * scale > maxHeight) {
//                        scale = maxHeight / mediaVideo.getPreviewH();
//                    }
//
//                    int scaledW = (int) maxWidth;
//                    int scaledH = (int) (mediaVideo.getPreviewH() * scale);
//
//                    previewTask = new ScaleTask(
//                            new VideoThumbTask(application.getDownloadManager().getFileName(key))
//                            , scaledW, scaledH);
//                    previewTask.setPutInDiskCache(true);
//
//                    previewWidth = mediaVideo.getPreviewW();
//                    previewHeight = mediaVideo.getPreviewH();
                }
            }

//            if (mediaVideo.getPreviewW() != 0 && mediaVideo.getPreviewH() != 0) {
//                if (mediaVideo.getFastPreview().length != 0) {
//                    float maxWidth = getPx(160);
//                    float maxHeight = getPx(300);
//
//                    float scale = maxWidth / mediaVideo.getPreviewW();
//
//                    if (mediaVideo.getPreviewH() * scale > maxHeight) {
//                        scale = maxHeight / mediaVideo.getPreviewH();
//                    }
//
//                    int scaledW = (int) maxWidth;
//                    int scaledH = (int) (mediaVideo.getPreviewH() * scale);
//
//                    previewHeight = scaledH;
//                    previewWidth = scaledW;
//
//                    if (previewCached == null) {
//                        BitmapFactory.Options options = new BitmapFactory.Options();
//                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//                        options.inMutable = true;
//                        options.inDither = false;
//                        options.inTempStorage = bitmapTmp;
//                        Bitmap img = BitmapFactory.decodeByteArray(mediaVideo.getFastPreview(), 0, mediaVideo.getFastPreview().length, options);
////                        if (previewBitmapHolder != null) {
////                            previewCached = previewBitmapHolder;
////                            previewBitmapHolder = null;
////                            optimizedBlur.performBlur(img);
////                            //BitmapUtils.fastblur(img, previewCached, mediaVideo.getPreviewW(), mediaVideo.getPreviewH(), 3);
////                        } else {
////                            previewCached = img.copy(Bitmap.Config.ARGB_8888, true);
////                            optimizedBlur.performBlur(previewCached);
////                            // BitmapUtils.fastblur(img, previewCached, mediaVideo.getPreviewW(), mediaVideo.getPreviewH(), 3);
////                        }
//
//                        // previewCached = img.copy(Bitmap.Config.ARGB_8888, true);
//                        optimizedBlur.performBlur(img);
//                        previewCached = img;
//                        fastPreviewWidth = mediaVideo.getPreviewW() - 1;
//                        fastPreviewHeight = mediaVideo.getPreviewH() - 1;
//                    }
//                } else {
//                    if (previewTask == null) {
//                        if (mediaVideo.getPreviewLocation() instanceof TLLocalFileLocation) {
//                            TLLocalFileLocation location = (TLLocalFileLocation) mediaVideo.getPreviewLocation();
//
//                            previewWidth = mediaVideo.getPreviewW();
//                            previewHeight = mediaVideo.getPreviewH();
//
//                            float maxWidth = getPx(160);
//                            float maxHeight = getPx(300);
//
//                            float scale = maxWidth / previewWidth;
//
//                            if (previewHeight * scale > maxHeight) {
//                                scale = maxHeight / previewHeight;
//                            }
//
//                            int scaledW = (int) maxWidth;
//                            int scaledH = (int) (previewHeight * scale);
//
//                            StelsImageTask baseTask = new StelsImageTask(new TLFileLocation(location.getDcId(), location.getVolumeId(), location.getLocalId(), location.getSecret()));
//                            baseTask.enableBlur(3);
//                            previewTask = new ScaleTask(baseTask, scaledW, scaledH);
//                        }
//                    }
//                }
//            }
        } else if (message.message.getExtras() instanceof TLUploadingPhoto) {
            TLUploadingPhoto photo = (TLUploadingPhoto) message.message.getExtras();
            bindSize(photo.getWidth(), photo.getHeight());
//            previewWidth = photo.getWidth();
//            previewHeight = photo.getHeight();
//
//            float maxWidth = getPx(160);
//            float maxHeight = getPx(300);
//
//            float scale = maxWidth / previewWidth;
//
//            if (previewHeight * scale > maxHeight) {
//                scale = maxHeight / previewHeight;
//            }
//
//            int scaledW = (int) maxWidth;
//            int scaledH = (int) (previewHeight * scale);
//
//            if (photo.getFileUri() != null && photo.getFileUri().length() > 0) {
//                previewTask = new ScaleTask(new UriImageTask(photo.getFileUri()), scaledW, scaledH);
//            } else if (photo.getFileName() != null && photo.getFileName().length() > 0) {
//                previewTask = new ScaleTask(new FileSystemImageTask(photo.getFileName()), scaledW, scaledH);
//            }

            isUploadable = true;
        } else if (message.message.getExtras() instanceof TLUploadingVideo) {
            TLUploadingVideo video = (TLUploadingVideo) message.message.getExtras();
            bindSize(video.getPreviewWidth(), video.getPreviewHeight());
//            previewWidth = video.getPreviewWidth();
//            previewHeight = video.getPreviewHeight();
//
//
//            float maxWidth = getPx(160);
//            float maxHeight = getPx(300);
//
//            float scale = maxWidth / previewWidth;
//
//            if (previewHeight * scale > maxHeight) {
//                scale = maxHeight / previewHeight;
//            }
//
//            int scaledW = (int) maxWidth;
//            int scaledH = (int) (previewHeight * scale);
//
//            previewTask = new ScaleTask(new VideoThumbTask(video.getFileName()), scaledW, scaledH);
            isUploadable = true;
        } else if (message.message.getExtras() instanceof TLLocalGeo) {
            TLLocalGeo geo = (TLLocalGeo) message.message.getExtras();
            bindSize(getPx(160), getPx(160));
//            previewWidth = getPx(160);
//            previewHeight = getPx(160);
//
//            int imageW = previewWidth / 2;
//            int imageH = previewHeight / 2;
//
//            previewTask = new ScaleTask(new ImageDownloadTask(buildMapUrl(geo.getLatitude(), geo.getLongitude(), imageW, imageH)), previewWidth, previewHeight);
            showMapPoint = true;
        } else if (message.message.getExtras() instanceof TLUploadingDocument) {

            TLUploadingDocument doc = (TLUploadingDocument) message.message.getExtras();

            bindSize(doc.getFullPreviewW(), doc.getFullPreviewH());

//            if (doc.getFilePath().length() > 0) {
//                previewTask = new ScaleTask(new FileSystemImageTask(doc.getFilePath()), scaledW, scaledH);
//            } else {
//                previewTask = new ScaleTask(new UriImageTask(doc.getFileUri()), scaledW, scaledH);
//            }

            isUploadable = true;
        } else if (message.message.getExtras() instanceof TLLocalDocument) {
            TLLocalDocument document = (TLLocalDocument) message.message.getExtras();

            bindSize(document.getPreviewW(), document.getPreviewH());

            key = DownloadManager.getDocumentKey(document);
            isDownloadable = true;

//            if (document.getPreviewW() != 0 && document.getPreviewH() != 0) {
//                previewWidth = document.getPreviewW();
//                previewHeight = document.getPreviewH();
//
//                if (document.getMimeType().equals("image/gif") ||
//                        document.getMimeType().equals("image/png") ||
//                        document.getMimeType().equals("image/jpeg")) {
//                    if (application.getDownloadManager().getState(key) == DownloadState.COMPLETED) {
//                        float maxWidth = getPx(160);
//                        float maxHeight = getPx(300);
//
//                        float scale = maxWidth / previewWidth;
//
//                        if (previewHeight * scale > maxHeight) {
//                            scale = maxHeight / previewHeight;
//                        }
//
//                        int scaledW = (int) maxWidth;
//                        int scaledH = (int) (previewHeight * scale);
//
//                        previewTask = new ScaleTask(new FileSystemImageTask(application.getDownloadManager().getFileName(key)), scaledW, scaledH);
//                        previewTask.setPutInDiskCache(true);
//                    }
//                }
//
//                if (document.getFastPreview().length > 0) {
//                    if (previewCached == null) {
//                        BitmapFactory.Options options = new BitmapFactory.Options();
//                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
//                        options.inDither = false;
//                        options.inTempStorage = bitmapTmp;
//                        options.inMutable = true;
//                        Bitmap img = BitmapFactory.decodeByteArray(document.getFastPreview(), 0, document.getFastPreview().length, options);
//                        previewCached = optimizedBlur.performBlur(img);
//                        // previewCached = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.ARGB_8888);
//                        // BitmapUtils.fastblur(img, previewCached, img.getWidth(), img.getHeight(), 3);
//                    }
//                    fastPreviewWidth = document.getPreviewW() - 1;
//                    fastPreviewHeight = document.getPreviewH() - 1;
//                } else if (document.getPreviewLocation() instanceof TLLocalFileLocation) {
//                    if (previewTask == null) {
//
//                        float maxWidth = getPx(160);
//                        float maxHeight = getPx(300);
//
//                        float scale = maxWidth / previewWidth;
//
//                        if (previewHeight * scale > maxHeight) {
//                            scale = maxHeight / previewHeight;
//                        }
//
//                        int scaledW = (int) maxWidth;
//                        int scaledH = (int) (previewHeight * scale);
//
//                        StelsImageTask baseTask = new StelsImageTask((TLLocalFileLocation) document.getPreviewLocation());
//                        baseTask.enableBlur(3);
//                        previewTask = new ScaleTask(baseTask, scaledW, scaledH);
//                    }
//                }
//            }
        } else {
            isUnsupported = true;
            bindSize(0, 0);
        }

        if (!isBindSizeCalled) {
            throw new RuntimeException("bindSize not called");
        }

        if (isDownloadable) {
            if (key != null) {
                DownloadState state = application.getDownloadManager().getState(key);
                if (downloadProgress != application.getDownloadManager().getDownloadProgress(key)) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = application.getDownloadManager().getDownloadProgress(key);
                    downloadStateTime = SystemClock.uptimeMillis();
                }

                switch (state) {
                    case CANCELLED:
                        downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                        break;
                    case FAILURE:
                        downloadString = getResources().getString(R.string.st_bubble_media_try_again);
                        break;
                    case NONE:
                        downloadString = getResources().getString(R.string.st_bubble_media_download);
                        break;
                    case IN_PROGRESS:
                    case PENDING:
                        downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                        break;
                    case COMPLETED:
                        downloadString = null;
                        break;
                }
            }
        }

        if (isUploadable) {
            MediaSender.SendState state = application.getMediaSender().getSendState(databaseId);
            if (state != null) {
                if (downloadProgress != state.getUploadProgress()) {
                    oldDownloadProgress = downloadProgress;
                    downloadProgress = state.getUploadProgress();
                    downloadStateTime = SystemClock.uptimeMillis();
                }
                if (state.isCanceled()) {
                    downloadString = getResources().getString(R.string.st_bubble_media_cancelled);
                } else if (state.isUploaded()) {
                    downloadString = null;
                } else {
                    downloadString = getResources().getString(R.string.st_bubble_media_in_progress);
                }
            }
        }
    }

    @Override
    protected void bindNewView(MessageWireframe message) {
        if (Build.VERSION.SDK_INT >= 11) {
            if (message.message.getRawContentType() == ContentType.MESSAGE_DOC_ANIMATED) {
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            } else {
                setLayerType(LAYER_TYPE_NONE, null);
            }
        }
        long start = SystemClock.uptimeMillis();

        this.databaseId = message.message.getDatabaseId();
        this.isOut = message.message.isOut();
        this.date = TextUtil.formatTime(message.message.getDate(), getContext());
        if (isOut) {
            placeholderPaint.setColor(0xffe6ffd1);
        } else {
            placeholderPaint.setColor(Color.WHITE);
        }

        unbindOldPreview();
        unbindPreview();

        this.state = message.message.getState();
        this.prevState = -1;
        this.oldPreview = null;

        bindMedia(message);

        this.downloadStateTime = 0;

        Logger.d(TAG, "Bind in " + (SystemClock.uptimeMillis() - start) + " ms");
        requestLayout();
    }

    @Override
    protected void bindUpdate(MessageWireframe message) {

        if (this.state != message.message.getState()) {
            this.prevState = this.state;
            this.state = message.message.getState();
            this.stateChangeTime = SystemClock.uptimeMillis();
        }

//        if (preview != null && preview != oldPreview) {
//            this.oldPreview = preview;
//        }
        bindMedia(message);

        invalidate();
    }

    public void toggleMovie() {
        if (lastDatabaseId != databaseId) {
            lastDatabaseId = databaseId;
            lastMovieStart = 0;
            lastMovie = null;
            moviePaused = false;
            movieLoader.execute(new Runnable() {
                @Override
                public void run() {
                    if (lastDatabaseId != databaseId) {
                        return;
                    }

                    byte[] data;
                    try {
                        data = IOUtils.readAll(application.getDownloadManager().getFileName(key));
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    final Movie fmovie = Movie.decodeByteArray(data, 0, data.length);

                    post(new Runnable() {
                        @Override
                        public void run() {
                            if (lastDatabaseId != databaseId) {
                                return;
                            }

                            moviePaused = false;
                            lastMovie = fmovie;
                            lastMovieStart = System.currentTimeMillis();
                            invalidate();
                        }
                    });
                }
            });
        } else {
            if (moviePaused) {
                moviePaused = false;
                lastMovieStart = System.currentTimeMillis();
            } else {
                moviePaused = true;
            }
        }
        invalidate();
    }

    @Override
    protected void measureBubbleContent(int width) {
//        float maxWidth = getPx(160);
//        float maxHeight = getPx(300);

//        if (previewWidth != 0 && previewHeight != 0) {
//            float scale = maxWidth / previewWidth;
//
//            if (previewHeight * scale > maxHeight) {
//                scale = maxHeight / previewHeight;
//            }
//
//            desiredWidth = (int) maxWidth;
//            desiredHeight = (int) (previewHeight * scale);
//        } else {
//            desiredWidth = (int) maxWidth;
//            desiredHeight = getPx(115);
//        }

        int centerX = desiredWidth / 2;
        int centerY = desiredHeight / 2;
        int outerR = getPx(20);

        path.reset();
        path.addRect(0, 0, desiredWidth, desiredHeight, Path.Direction.CW);
        path.close();
        path.addCircle(centerX, centerY, outerR, Path.Direction.CW);
        path.close();
        path.setFillType(Path.FillType.EVEN_ODD);

        timeWidth = (int) timePaint.measureText(date);
        if (isOut) {
            timeWidth += getPx(18);
        }

        setBubbleMeasuredContent(desiredWidth, desiredHeight);
    }

    @Override
    protected int getInBubbleResource() {
        return R.drawable.st_bubble_in_media_normal;
    }

    @Override
    protected int getOutBubbleResource() {
        return R.drawable.st_bubble_out_media_normal;
    }

    @Override
    protected int getOutPressedBubbleResource() {
        return R.drawable.st_bubble_out_media_overlay;
    }

    @Override
    protected int getInPressedBubbleResource() {
        return R.drawable.st_bubble_in_media_overlay;
    }

    private Drawable getStateDrawable(int state) {
        switch (state) {
            default:
            case MessageState.SENT:
                return stateSent;
            case MessageState.READED:
                return stateHalfCheck;
            case MessageState.FAILURE:
                return stateFailure;
        }
    }


    @Override
    protected boolean drawBubble(Canvas canvas) {
        boolean isAnimated = false;

        long animationTime = SystemClock.uptimeMillis() - previewAppearTime;
        Movie movie = lastMovie;
        if (movie != null && lastDatabaseId == databaseId) {
            // if (movie.duration() != 0) {
            if (!moviePaused) {
                int duration = movie.duration();
                if (duration == 0) {
                    duration = 1000;
                }
                movie.setTime((int) ((System.currentTimeMillis() - lastMovieStart) % duration));
                isAnimated = true;
            }
            //}
            canvas.drawRect(0, 0, desiredWidth, desiredHeight, placeholderPaint);
            if (movie.width() != 0 && movie.height() != 0) {
                canvas.save();
                float scale = Math.min((float) desiredWidth / movie.width(), (float) desiredHeight / movie.height());
                canvas.translate((desiredWidth - scale * movie.width()) / 2, (desiredHeight - scale * movie.height()) / 2);
                canvas.scale(scale, scale);
                movie.draw(canvas, 0, 0);
                canvas.restore();
            } else {
                movie.draw(canvas, 0, 0);
            }
        } else if (preview != null) {
            if (animationTime > FADE_ANIMATION_TIME || !isAnimatedProgress) {
                bitmapFilteredPaint.setAlpha(255);
                rect1.set(0, 0, previewRegionW, previewRegionH);
                rect2.set(0, 0, desiredWidth, desiredHeight);
                canvas.drawBitmap(preview, rect1, rect2, bitmapFilteredPaint);
            } else {
                float alpha = fadeEasing((float) animationTime / FADE_ANIMATION_TIME);

                if (oldPreview != null) {
                    bitmapFilteredPaint.setAlpha(255);
                    rect1.set(0, 0, oldPreviewRegionW, oldPreviewRegionH);
                    rect2.set(0, 0, desiredWidth, desiredHeight);
                    canvas.drawBitmap(oldPreview, rect1, rect2, bitmapFilteredPaint);
                } else {
                    canvas.drawRect(0, 0, desiredWidth, desiredHeight, placeholderPaint);
                }
//                } else if (previewCached != null) {
//                    bitmapFilteredPaint.setAlpha(255);
//                    rect1.set(0, 0, fastPreviewWidth, fastPreviewHeight);
//                    rect2.set(0, 0, desiredWidth, desiredHeight);
//                    canvas.drawBitmap(previewCached, rect1, rect2, bitmapFilteredPaint);
//                } else {
//                    canvas.drawRect(0, 0, desiredWidth, desiredHeight, placeholderPaint);
//                }

                bitmapFilteredPaint.setAlpha((int) (255 * alpha));
                rect1.set(0, 0, previewRegionW, previewRegionH);
                rect2.set(0, 0, desiredWidth, desiredHeight);
                canvas.drawBitmap(preview, rect1, rect2, bitmapFilteredPaint);
//                if (scaleUpMedia) {
//                    rect1.set(0, 0, preview.getWidth(), preview.getHeight());
//                    rect2.set(0, 0, desiredWidth, desiredHeight);
//                    canvas.drawBitmap(preview, rect1, rect2, bitmapPaint);
//                } else {
//                    canvas.drawBitmap(preview, 0, 0, bitmapPaint);
//                }

                isAnimated = true;
            }
        } else if (oldPreview != null) {
            bitmapPaint.setAlpha(255);
            if (scaleUpMedia) {
                rect1.set(0, 0, oldPreview.getWidth(), oldPreview.getHeight());
                rect2.set(0, 0, desiredWidth, desiredHeight);
                canvas.drawBitmap(oldPreview, rect1, rect2, bitmapPaint);
            } else {
                canvas.drawBitmap(oldPreview, 0, 0, bitmapPaint);
            }
        } else {
            canvas.drawRect(0, 0, desiredWidth, desiredHeight, placeholderPaint);
        }
//        } else if (previewCached != null) {
//            bitmapFilteredPaint.setAlpha(255);
//            rect1.set(0, 0, fastPreviewWidth, fastPreviewHeight);
//            rect2.set(0, 0, desiredWidth, desiredHeight);
//            canvas.drawBitmap(previewCached, rect1, rect2, bitmapFilteredPaint);
//        } else {
//            canvas.drawRect(0, 0, desiredWidth, desiredHeight, placeholderPaint);
//        }

        if (showMapPoint) {
            mapPoint.setBounds((desiredWidth - mapPoint.getIntrinsicWidth()) / 2,
                    desiredHeight / 2 - mapPoint.getIntrinsicHeight(),
                    (desiredWidth + mapPoint.getIntrinsicWidth()) / 2,
                    desiredHeight / 2);
            mapPoint.draw(canvas);
        }

        if (isUnsupported) {
            unsupportedMark.setBounds((desiredWidth - unsupportedMark.getIntrinsicWidth()) / 2,
                    (desiredHeight - unsupportedMark.getIntrinsicHeight()) / 2,
                    (desiredWidth + unsupportedMark.getIntrinsicWidth()) / 2,
                    (desiredHeight + unsupportedMark.getIntrinsicHeight()) / 2);
            unsupportedMark.draw(canvas);
        }

        if (isDownloadable || isUploadable) {
            long downloadProgressAnimationTime = SystemClock.uptimeMillis() - downloadStateTime;
            if (downloadProgress < 100 || downloadProgressAnimationTime < FADE_ANIMATION_TIME) {
                isAnimated = true;
                int internalR = getPx(16);
                int outerR = getPx(20);

                if (downloadProgress == 100 && isAnimatedProgress) {
                    float alpha = fadeEasing((float) downloadProgressAnimationTime / FADE_ANIMATION_TIME);
                    float scale = scaleEasing((float) downloadProgressAnimationTime / FADE_ANIMATION_TIME);
//                    downloadBgRect.setAlpha((int) (0xB6 * (1 - alpha)));
                    downloadBgRect.setAlpha(0xB6);
                    downloadBgLightRect.setAlpha((int) (0x30 * (1 - alpha)));

                    int maxR = (int) Math.sqrt((desiredWidth * desiredWidth + desiredHeight * desiredHeight) / 4);
                    outerR = (int) (getPx(20) + (maxR * (scale)));
                    internalR = (int) (getPx(16) * (1 - scale));
                } else {
                    downloadBgRect.setAlpha(0xB6);
                    downloadBgLightRect.setAlpha(0x30);
                }

                canvas.save();
                canvas.clipRect(0, 0, desiredWidth, desiredHeight);

                float currentDownloadProgress = downloadProgress;


                if (downloadProgressAnimationTime < STATE_ANIMATION_TIME && isAnimatedProgress) {
                    float alpha = fadeEasing((float) downloadProgressAnimationTime / STATE_ANIMATION_TIME);
                    isAnimated = true;
                    currentDownloadProgress = oldDownloadProgress + (downloadProgress - oldDownloadProgress) * alpha;
                }

                int centerX = desiredWidth / 2;
                int centerY = desiredHeight / 2;

                // canvas.drawPath(path, downloadBgRect);

                canvas.drawCircle(centerX, centerY, outerR, downloadBgLightRect);

                rectF.set(centerX - internalR, centerY - internalR, centerX + internalR, centerY + internalR);
                int progressAngleStart = -90;
                int progressAngle = (int) (-360 + (currentDownloadProgress * 360 / 100));
                canvas.drawArc(rectF, progressAngleStart, progressAngle, true, downloadBgRect);
                canvas.restore();
            }

            if (downloadString != null) {
                int textW = (int) downloadPaint.measureText(downloadString);
                Paint.FontMetricsInt metricsInt = downloadPaint.getFontMetricsInt();
                canvas.drawText(downloadString, (desiredWidth - textW) / 2, desiredHeight / 2 + (/*metricsInt.bottom*/ -metricsInt.ascent) / 2 + getPx(24), downloadPaint);
            }
        }

        if (isVideo) {
            videoIcon.setBounds(getPx(8), getPx(6), getPx(8) + videoIcon.getIntrinsicWidth(), getPx(6) + videoIcon.getIntrinsicHeight());
            videoIcon.draw(canvas);
            canvas.drawText(duration, getPx(27), getPx(18), videoDurationPaint);
        }

        canvas.drawRect(desiredWidth - timeWidth - getPx(17), desiredHeight - getPx(25), desiredWidth - getPx(3), desiredHeight - getPx(3), timeBgRect);
        canvas.drawText(date, desiredWidth - timeWidth - getPx(10), desiredHeight - getPx(9), timePaint);
        if (isOut) {
            if (state == MessageState.PENDING) {
                canvas.save();
                canvas.translate(desiredWidth - getPx(12 + 8), desiredHeight - getPx(19));
                canvas.drawCircle(getPx(6), getPx(6), getPx(6), clockIconPaint);
                double time = (System.currentTimeMillis() / 15.0) % (12 * 60);
                double angle = (time / (6 * 60)) * Math.PI;

                int x = (int) (Math.sin(-angle) * getPx(4));
                int y = (int) (Math.cos(-angle) * getPx(4));
                canvas.drawLine(getPx(6), getPx(6), getPx(6) + x, getPx(6) + y, clockIconPaint);

                x = (int) (Math.sin(-angle * 12) * getPx(5));
                y = (int) (Math.cos(-angle * 12) * getPx(5));
                canvas.drawLine(getPx(6), getPx(6), getPx(6) + x, getPx(6) + y, clockIconPaint);
                canvas.restore();
                isAnimated = true;
            } else if (state == MessageState.READED && prevState == MessageState.SENT && (SystemClock.uptimeMillis() - stateChangeTime < FADE_ANIMATION_TIME)) {
                long stateAnimationTime = SystemClock.uptimeMillis() - stateChangeTime;
                float progress = easeStateFade(stateAnimationTime / (float) STATE_ANIMATION_TIME);
                int offset = (int) (getPx(5) * progress);
                int alphaNew = (int) (progress * 255);

                bounds(stateSent, desiredWidth - getPx(8) - stateSent.getIntrinsicWidth() - offset,
                        desiredHeight - getPx(8) - stateSent.getIntrinsicHeight());
                stateSent.setAlpha(255);
                stateSent.draw(canvas);

                bounds(stateHalfCheck, desiredWidth - getPx(8) - stateHalfCheck.getIntrinsicWidth(),
                        desiredHeight - getPx(8) - stateHalfCheck.getIntrinsicHeight());
                stateHalfCheck.setAlpha(alphaNew);
                stateHalfCheck.draw(canvas);

                isAnimated = true;
            } else {
                Drawable drawable = getStateDrawable(state);

                if (state == MessageState.READED) {
                    bounds(stateSent, desiredWidth - getPx(8) - stateSent.getIntrinsicWidth() - getPx(5),
                            desiredHeight - getPx(8) - stateSent.getIntrinsicHeight());
                    stateSent.setAlpha(255);
                    stateSent.draw(canvas);
                }

                bounds(drawable, desiredWidth - getPx(8) - drawable.getIntrinsicWidth(), desiredHeight - getPx(8) - drawable.getIntrinsicHeight());
                drawable.setAlpha(255);
                drawable.draw(canvas);
            }
        }

        return isAnimated;
    }

    @Override
    public void onMediaReceived(Bitmap preview, int regionW, int regionH, String key, boolean intermediate) {
        if (this.preview != preview) {
            unbindOldPreview();
            this.oldPreview = this.preview;
            this.oldPreviewKey = this.previewKey;
            this.oldPreviewRegionW = this.previewRegionW;
            this.oldPreviewRegionH = this.previewRegionH;
        }
        this.preview = preview;
        this.previewKey = key;
        this.previewRegionW = regionW;
        this.previewRegionH = regionH;
        application.getUiKernel().getMediaLoader().getImageCache().incReference(key);
        invalidate();
    }
}