// Generated by view binder compiler. Do not edit!
package com.shenji.aikeyboard.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import com.shenji.aikeyboard.R;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ActivityMainBinding implements ViewBinding {
  @NonNull
  private final LinearLayout rootView;

  @NonNull
  public final FrameLayout aiTestButtonContainer;

  @NonNull
  public final ImageView appIconTop;

  @NonNull
  public final FrameLayout assistsTestButtonContainer;

  @NonNull
  public final FrameLayout gemma3nButtonContainer;

  @NonNull
  public final FrameLayout inputMethodSettingsButtonContainer;

  @NonNull
  public final FrameLayout logDetailButtonContainer;

  @NonNull
  public final FrameLayout mnnInferenceButtonContainer;

  @NonNull
  public final FrameLayout optimizedCandidateTestButtonContainer;

  private ActivityMainBinding(@NonNull LinearLayout rootView,
      @NonNull FrameLayout aiTestButtonContainer, @NonNull ImageView appIconTop,
      @NonNull FrameLayout assistsTestButtonContainer, @NonNull FrameLayout gemma3nButtonContainer,
      @NonNull FrameLayout inputMethodSettingsButtonContainer,
      @NonNull FrameLayout logDetailButtonContainer,
      @NonNull FrameLayout mnnInferenceButtonContainer,
      @NonNull FrameLayout optimizedCandidateTestButtonContainer) {
    this.rootView = rootView;
    this.aiTestButtonContainer = aiTestButtonContainer;
    this.appIconTop = appIconTop;
    this.assistsTestButtonContainer = assistsTestButtonContainer;
    this.gemma3nButtonContainer = gemma3nButtonContainer;
    this.inputMethodSettingsButtonContainer = inputMethodSettingsButtonContainer;
    this.logDetailButtonContainer = logDetailButtonContainer;
    this.mnnInferenceButtonContainer = mnnInferenceButtonContainer;
    this.optimizedCandidateTestButtonContainer = optimizedCandidateTestButtonContainer;
  }

  @Override
  @NonNull
  public LinearLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivityMainBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivityMainBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_main, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivityMainBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.aiTestButtonContainer;
      FrameLayout aiTestButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (aiTestButtonContainer == null) {
        break missingId;
      }

      id = R.id.appIconTop;
      ImageView appIconTop = ViewBindings.findChildViewById(rootView, id);
      if (appIconTop == null) {
        break missingId;
      }

      id = R.id.assistsTestButtonContainer;
      FrameLayout assistsTestButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (assistsTestButtonContainer == null) {
        break missingId;
      }

      id = R.id.gemma3nButtonContainer;
      FrameLayout gemma3nButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (gemma3nButtonContainer == null) {
        break missingId;
      }

      id = R.id.inputMethodSettingsButtonContainer;
      FrameLayout inputMethodSettingsButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (inputMethodSettingsButtonContainer == null) {
        break missingId;
      }

      id = R.id.logDetailButtonContainer;
      FrameLayout logDetailButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (logDetailButtonContainer == null) {
        break missingId;
      }

      id = R.id.mnnInferenceButtonContainer;
      FrameLayout mnnInferenceButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (mnnInferenceButtonContainer == null) {
        break missingId;
      }

      id = R.id.optimizedCandidateTestButtonContainer;
      FrameLayout optimizedCandidateTestButtonContainer = ViewBindings.findChildViewById(rootView, id);
      if (optimizedCandidateTestButtonContainer == null) {
        break missingId;
      }

      return new ActivityMainBinding((LinearLayout) rootView, aiTestButtonContainer, appIconTop,
          assistsTestButtonContainer, gemma3nButtonContainer, inputMethodSettingsButtonContainer,
          logDetailButtonContainer, mnnInferenceButtonContainer,
          optimizedCandidateTestButtonContainer);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
