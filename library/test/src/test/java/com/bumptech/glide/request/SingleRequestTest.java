package com.bumptech.glide.request;

import static com.bumptech.glide.tests.Util.isADataSource;
import static com.bumptech.glide.tests.Util.mockResource;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.bumptech.glide.GlideContext;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.Engine;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.transition.TransitionFactory;
import com.bumptech.glide.signature.ObjectKey;
import com.google.common.base.Equivalence;
import com.google.common.testing.EquivalenceTester;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 18)
@SuppressWarnings("rawtypes")
public class SingleRequestTest {

  private SingleRequestBuilder builder;

  @Before
  public void setUp() {
    builder = new SingleRequestBuilder();
  }

  @Test
  public void testIsNotCompleteBeforeReceivingResource() {
    SingleRequest<List> request = builder.build();

    assertFalse(request.isComplete());
  }

  @Test
  public void testCanHandleNullResources() {
    SingleRequest<List> request = builder.build();

    request.onResourceReady(null, DataSource.LOCAL);

    assertTrue(request.isFailed());
    verify(builder.requestListener).onLoadFailed(isAGlideException(), isA(Number.class),
        eq(builder.target), anyBoolean());
  }

  @Test
  public void testCanHandleEmptyResources() {
    SingleRequest<List> request = builder.build();
    when(builder.resource.get()).thenReturn(null);

    request.onResourceReady(builder.resource, DataSource.REMOTE);

    assertTrue(request.isFailed());
    verify(builder.engine).release(eq(builder.resource));
    verify(builder.requestListener).onLoadFailed(isAGlideException(), any(Number.class),
        eq(builder.target), anyBoolean());
  }

  @Test
  public void testCanHandleNonConformingResources() {
    SingleRequest<List> request = builder.build();
    when(((Resource) (builder.resource)).get())
        .thenReturn("Invalid mocked String, this should be a List");

    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    assertTrue(request.isFailed());
    verify(builder.engine).release(eq(builder.resource));
    verify(builder.requestListener).onLoadFailed(isAGlideException(), any(Number.class),
        eq(builder.target), anyBoolean());
  }

  @Test
  public void testIsNotFailedAfterClear() {
    SingleRequest<List> request = builder.build();

    request.onResourceReady(null, DataSource.DATA_DISK_CACHE);
    request.clear();

    assertFalse(request.isFailed());
  }

  @Test
  public void testIsPausedAfterPause() {
    SingleRequest<List> request = builder.build();
    request.pause();

    assertTrue(request.isPaused());
  }

  @Test
  public void testIsNotCancelledAfterPause() {
    SingleRequest<List> request = builder.build();
    request.pause();

    assertFalse(request.isCancelled());
  }

  @Test
  public void testIsNotPausedAfterBeginningWhilePaused() {
    SingleRequest<List> request = builder.build();
    request.pause();
    request.begin();

    assertFalse(request.isPaused());
    assertTrue(request.isRunning());
  }

  @Test
  public void testIsNotFailedAfterBegin() {
    SingleRequest<List> request = builder.build();

    request.onResourceReady(null, DataSource.DATA_DISK_CACHE);
    request.begin();

    assertFalse(request.isFailed());
  }

  @Test
  public void testIsCompleteAfterReceivingResource() {
    SingleRequest<List> request = builder.build();

    request.onResourceReady(builder.resource, DataSource.LOCAL);

    assertTrue(request.isComplete());
  }

  @Test
  public void testIsNotCompleteAfterClear() {
    SingleRequest<List> request = builder.build();
    request.onResourceReady(builder.resource, DataSource.REMOTE);
    request.clear();

    assertFalse(request.isComplete());
  }

  @Test
  public void testIsCancelledAfterClear() {
    SingleRequest<List> request = builder.build();
    request.clear();

    assertTrue(request.isCancelled());
  }

  @Test
  public void clear_notifiesTarget() {
    SingleRequest<List> request = builder.build();
    request.clear();

    verify(builder.target).onLoadCleared(any(Drawable.class));
  }

  @Test
  public void testDoesNotNotifyTargetTwiceIfClearedTwiceInARow() {
    SingleRequest<List> request = builder.build();
    request.clear();
    request.clear();

    verify(builder.target, times(1)).onLoadCleared(any(Drawable.class));
  }

  @Test
  public void clear_doesNotNotifyTarget_ifRequestCoordinatorReturnsFalseForCanClear() {
    when(builder.requestCoordinator.canNotifyCleared(any(Request.class))).thenReturn(false);
    SingleRequest<List> request = builder.build();
    request.clear();

    verify(builder.target, never()).onLoadCleared(any(Drawable.class));
  }

  @Test
  public void testResourceIsNotCompleteWhenAskingCoordinatorIfCanSetImage() {
    RequestCoordinator requestCoordinator = mock(RequestCoordinator.class);
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) {
        Request request = (Request) invocation.getArguments()[0];
        assertFalse(request.isComplete());
        return true;
      }
    }).when(requestCoordinator).canSetImage(any(Request.class));

    SingleRequest<List> request = builder
        .setRequestCoordinator(requestCoordinator)
        .build();

    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(requestCoordinator).canSetImage(eq(request));
  }

  @Test
  public void testIsNotFailedWithoutException() {
    SingleRequest<List> request = builder.build();

    assertFalse(request.isFailed());
  }

  @Test
  public void testIsFailedAfterException() {
    SingleRequest<List> request = builder.build();

    request.onLoadFailed(new GlideException("test"));
    assertTrue(request.isFailed());
  }

  @Test
  public void testIgnoresOnSizeReadyIfNotWaitingForSize() {
    SingleRequest<List> request = builder.build();
    request.begin();
    request.onSizeReady(100, 100);
    request.onSizeReady(100, 100);

    verify(builder.engine, times(1))
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            eq(100),
            eq(100),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class));
  }

  @Test
  public void testIsFailedAfterNoResultAndNullException() {
    SingleRequest<List> request = builder.build();

    request.onLoadFailed(new GlideException("test"));
    assertTrue(request.isFailed());
  }

  @Test
  public void testEngineLoadCancelledOnCancel() {
    Engine.LoadStatus loadStatus = mock(Engine.LoadStatus.class);

    when(builder.engine
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class)))
        .thenReturn(loadStatus);

    SingleRequest<List> request = builder.build();
    request.begin();

    request.onSizeReady(100, 100);
    request.cancel();

    verify(loadStatus).cancel();
  }

  @Test
  public void testResourceIsRecycledOnClear() {
    SingleRequest<List> request = builder.build();

    request.onResourceReady(builder.resource, DataSource.REMOTE);
    request.clear();

    verify(builder.engine).release(eq(builder.resource));
  }

  @Test
  public void testPlaceholderDrawableIsSet() {
    Drawable expected = new ColorDrawable(Color.RED);

    MockTarget target = new MockTarget();

    SingleRequest<List> request = builder
        .setPlaceholderDrawable(expected)
        .setTarget(target)
        .build();
    request.begin();

    assertThat(target.currentPlaceholder).isEqualTo(expected);
  }

  @Test
  public void testErrorDrawableIsSetOnLoadFailed() {
    Drawable expected = new ColorDrawable(Color.RED);

    MockTarget target = new MockTarget();

    SingleRequest<List> request = builder
        .setErrorDrawable(expected)
        .setTarget(target)
        .build();

    request.onLoadFailed(new GlideException("test"));

    assertThat(target.currentPlaceholder).isEqualTo(expected);
  }

  @Test
  public void testPlaceholderDrawableSetOnNullModelWithNoErrorDrawable() {
    Drawable placeholder = new ColorDrawable(Color.RED);

    MockTarget target = new MockTarget();

    SingleRequest<List> request = builder
        .setErrorDrawable(placeholder)
        .setTarget(target)
        .setModel(null)
        .build();

    request.begin();

    assertThat(target.currentPlaceholder).isEqualTo(placeholder);
  }

  @Test
  public void testErrorDrawableSetOnNullModelWithErrorDrawable() {
    Drawable placeholder = new ColorDrawable(Color.RED);
    Drawable errorPlaceholder = new ColorDrawable(Color.GREEN);

    MockTarget target = new MockTarget();

    SingleRequest<List> request = builder
        .setPlaceholderDrawable(placeholder)
        .setErrorDrawable(errorPlaceholder)
        .setTarget(target)
        .setModel(null)
        .build();

    request.begin();

    assertThat(target.currentPlaceholder).isEqualTo(errorPlaceholder);
  }


  @Test
  public void testFallbackDrawableSetOnNullModelWithErrorAndFallbackDrawables() {
    Drawable placeholder = new ColorDrawable(Color.RED);
    Drawable errorPlaceholder = new ColorDrawable(Color.GREEN);
    Drawable fallback = new ColorDrawable(Color.BLUE);

    MockTarget target = new MockTarget();
    SingleRequest<List> request = builder
        .setPlaceholderDrawable(placeholder)
        .setErrorDrawable(errorPlaceholder)
        .setFallbackDrawable(fallback)
        .setTarget(target)
        .setModel(null)
        .build();
    request.begin();

    assertThat(target.currentPlaceholder).isEqualTo(fallback);
  }


  @Test
  public void testIsNotRunningBeforeRunCalled() {
    assertFalse(builder.build().isRunning());
  }

  @Test
  public void testIsRunningAfterRunCalled() {
    Request request = builder.build();
    request.begin();
    assertTrue(request.isRunning());
  }

  @Test
  public void testIsNotRunningAfterComplete() {
    SingleRequest<List> request = builder.build();
    request.begin();
    request.onResourceReady(builder.resource, DataSource.REMOTE);

    assertFalse(request.isRunning());
  }

  @Test
  public void testIsNotRunningAfterFailing() {
    SingleRequest<List> request = builder.build();
    request.begin();
    request.onLoadFailed(new GlideException("test"));

    assertFalse(request.isRunning());
  }

  @Test
  public void testIsNotRunningAfterClear() {
    SingleRequest<List> request = builder.build();
    request.begin();
    request.clear();

    assertFalse(request.isRunning());
  }

  @Test
  public void testCallsTargetOnResourceReadyIfNoRequestListener() {
    SingleRequest<List> request = builder
        .setRequestListener(null)
        .build();
    request.onResourceReady(builder.resource, DataSource.LOCAL);

    verify(builder.target).onResourceReady(eq(builder.result), anyTransition());
  }

  @Test
  public void testCallsTargetOnResourceReadyIfRequestListenerReturnsFalse() {
    SingleRequest<List> request = builder.build();
    when(builder.requestListener
        .onResourceReady(any(List.class), any(Number.class), eq(builder.target), isADataSource(),
            anyBoolean())).thenReturn(false);
    request.onResourceReady(builder.resource, DataSource.LOCAL);

    verify(builder.target).onResourceReady(eq(builder.result), anyTransition());
  }

  @Test
  public void testDoesNotCallTargetOnResourceReadyIfRequestListenerReturnsTrue() {
    SingleRequest<List> request = builder.build();
    when(builder.requestListener
        .onResourceReady(any(List.class), any(Number.class), eq(builder.target), isADataSource(),
            anyBoolean())).thenReturn(true);
    request.onResourceReady(builder.resource, DataSource.REMOTE);

    verify(builder.target, never()).onResourceReady(any(List.class), anyTransition());
  }

  @Test
  public void testCallsTargetOnExceptionIfNoRequestListener() {
    SingleRequest<List> request = builder
        .setRequestListener(null)
        .build();
    request.onLoadFailed(new GlideException("test"));

    verify(builder.target).onLoadFailed(eq(builder.errorDrawable));
  }

  @Test
  public void testCallsTargetOnExceptionIfRequestListenerReturnsFalse() {
    SingleRequest<List> request = builder.build();
    when(builder.requestListener.onLoadFailed(isAGlideException(), any(Number.class),
        eq(builder.target), anyBoolean()))
        .thenReturn(false);
    request.onLoadFailed(new GlideException("test"));

    verify(builder.target).onLoadFailed(eq(builder.errorDrawable));
  }

  @Test
  public void testDoesNotCallTargetOnExceptionIfRequestListenerReturnsTrue() {
    SingleRequest<List> request = builder.build();
    when(builder.requestListener.onLoadFailed(isAGlideException(), any(Number.class),
        eq(builder.target), anyBoolean()))
        .thenReturn(true);

    request.onLoadFailed(new GlideException("test"));

    verify(builder.target, never()).onLoadFailed(any(Drawable.class));
  }

  @Test
  public void testRequestListenerIsCalledWithResourceResult() {
    SingleRequest<List> request = builder.build();
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(), isADataSource(),
            anyBoolean());
  }

  @Test
  public void testRequestListenerIsCalledWithModel() {
    SingleRequest<List> request = builder.build();
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(any(List.class), eq(builder.model), isAListTarget(), isADataSource(),
            anyBoolean());
  }

  @Test
  public void testRequestListenerIsCalledWithTarget() {
    SingleRequest<List> request = builder.build();
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(any(List.class), any(Number.class), eq(builder.target), isADataSource(),
            anyBoolean());
  }

  @Test
  public void testRequestListenerIsCalledWithLoadedFromMemoryIfLoadCompletesSynchronously() {
    final SingleRequest<List> request = builder.build();

    when(builder.engine
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class)))
        .thenAnswer(new Answer<Object>() {
          @Override
          public Object answer(InvocationOnMock invocation) {
            request.onResourceReady(builder.resource, DataSource.MEMORY_CACHE);
            return null;
          }
        });

    request.begin();
    request.onSizeReady(100, 100);
    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(),
            eq(DataSource.MEMORY_CACHE), anyBoolean());
  }

  @Test
  public void
  testRequestListenerIsCalledWithNotLoadedFromMemoryCacheIfLoadCompletesAsynchronously() {
    SingleRequest<List> request = builder.build();
    request.onSizeReady(100, 100);
    request.onResourceReady(builder.resource, DataSource.LOCAL);

    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(),
            eq(DataSource.LOCAL), anyBoolean());
  }

  @Test
  public void testRequestListenerIsCalledWithIsFirstResourceIfNoRequestCoordinator() {
    SingleRequest<List> request = builder
        .setRequestCoordinator(null)
        .build();
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(), isADataSource(),
            eq(true));
  }

  @Test
  public void testRequestListenerIsCalledWithFirstImageIfRequestCoordinatorReturnsNoResourceSet() {
    SingleRequest<List> request = builder.build();
    when(builder.requestCoordinator.isAnyResourceSet()).thenReturn(false);
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(), isADataSource(),
            eq(true));
  }

  @Test
  public void
  testRequestListenerIsCalledWithNotIsFirstRequestIfRequestCoordinatorReturnsResourceSet() {
    SingleRequest<List> request = builder.build();
    when(builder.requestCoordinator.isAnyResourceSet()).thenReturn(true);
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.requestListener)
        .onResourceReady(eq(builder.result), any(Number.class), isAListTarget(),
            isADataSource(), eq(false));
  }

  @Test
  public void testTargetIsCalledWithAnimationFromFactory() {
    SingleRequest<List> request = builder.build();
    Transition<List> transition = mockTransition();
    when(builder.transitionFactory.build(any(DataSource.class), anyBoolean()))
        .thenReturn(transition);
    request.onResourceReady(builder.resource, DataSource.DATA_DISK_CACHE);

    verify(builder.target).onResourceReady(eq(builder.result), eq(transition));
  }

  @Test
  public void testCallsGetSizeIfOverrideWidthIsLessThanZero() {
    SingleRequest<List> request = builder
        .setOverrideWidth(-1)
        .setOverrideHeight(100)
        .build();
    request.begin();

    verify(builder.target).getSize(any(SizeReadyCallback.class));
  }

  @Test
  public void testCallsGetSizeIfOverrideHeightIsLessThanZero() {
    SingleRequest<List> request = builder
        .setOverrideWidth(100)
        .setOverrideHeight(-1)
        .build();
    request.begin();

    verify(builder.target).getSize(any(SizeReadyCallback.class));
  }

  @Test
  public void testDoesNotCallGetSizeIfOverrideWidthAndHeightAreSet() {
    SingleRequest<List> request = builder
        .setOverrideWidth(100)
        .setOverrideHeight(100)
        .build();
    request.begin();

    verify(builder.target, never()).getSize(any(SizeReadyCallback.class));
  }

  @Test
  public void testCallsEngineWithOverrideWidthAndHeightIfSet() {
    SingleRequest<List> request = builder
        .setOverrideWidth(1)
        .setOverrideHeight(2)
        .build();
    request.begin();

    verify(builder.engine)
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class));
  }

  @Test
  public void testDoesNotSetErrorDrawableIfRequestCoordinatorDoesntAllowIt() {
    SingleRequest<List> request = builder
        .setErrorDrawable(new ColorDrawable(Color.RED))
        .build();
    when(builder.requestCoordinator.canNotifyStatusChanged(any(Request.class))).thenReturn(false);
    request.onLoadFailed(new GlideException("test"));

    verify(builder.target, never()).onLoadFailed(any(Drawable.class));
  }

  @Test
  public void testCanReRunCancelledRequests() {
    doAnswer(new CallSizeReady(100, 100)).when(builder.target)
        .getSize(any(SizeReadyCallback.class));

    when(builder.engine
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            eq(100),
            eq(100),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class)))
        .thenAnswer(new CallResourceCallback(builder.resource));
    SingleRequest<List> request = builder.build();

    request.begin();
    request.cancel();
    request.begin();

    verify(builder.target, times(2)).onResourceReady(eq(builder.result), anyTransition());
  }

  @Test
  public void testResourceOnlyReceivesOneGetOnResourceReady() {
    SingleRequest<List> request = builder.build();
    request.onResourceReady(builder.resource, DataSource.LOCAL);

    verify(builder.resource, times(1)).get();
  }

  @Test
  public void testDoesNotStartALoadIfOnSizeReadyIsCalledAfterCancel() {
    SingleRequest<List> request = builder.build();
    request.cancel();
    request.onSizeReady(100, 100);

    verify(builder.engine, never())
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            anyBoolean(),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class));
  }


  @Test
  public void testCallsSourceUnlimitedExecutorEngineIfOptionsIsSet() {
    doAnswer(new CallSizeReady(100, 100)).when(builder.target)
        .getSize(any(SizeReadyCallback.class));

    SingleRequest<List> request = builder
        .setUseUnlimitedSourceGeneratorsPool(true)
        .build();
    request.begin();

    verify(builder.engine)
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            eq(true),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class));
  }

  @Test
  public void testCallsSourceExecutorEngineIfOptionsIsSet() {
    doAnswer(new CallSizeReady(100, 100)).when(builder.target)
        .getSize(any(SizeReadyCallback.class));

    SingleRequest<List> request = builder
        .setUseUnlimitedSourceGeneratorsPool(false)
        .build();
    request.begin();

    verify(builder.engine)
        .load(
            eq(builder.glideContext),
            eq(builder.model),
            eq(builder.signature),
            anyInt(),
            anyInt(),
            eq(Object.class),
            eq(List.class),
            any(Priority.class),
            any(DiskCacheStrategy.class),
            eq(builder.transformations),
            anyBoolean(),
            anyBoolean(),
            any(Options.class),
            anyBoolean(),
            eq(false),
            /*useAnimationPool=*/ anyBoolean(),
            anyBoolean(),
            any(ResourceCallback.class));
  }

  @Test
  // Varargs
  @SuppressWarnings("unchecked")
  public void testIsEquivalentTo() {
    EquivalenceTester<SingleRequestBuilder> tester = EquivalenceTester
        .of(new Equivalence<SingleRequestBuilder>() {
          @Override
          protected boolean doEquivalent(
              @NonNull SingleRequestBuilder a, @NonNull SingleRequestBuilder b) {
            return a.build().isEquivalentTo(b.build()) && b.build().isEquivalentTo(a.build());
          }

          @Override
          protected int doHash(@NonNull SingleRequestBuilder listSingleRequest) {
            return 0;
          }
        });
    tester
        .addEquivalenceGroup(
            new SingleRequestBuilder(),
            new SingleRequestBuilder(),
            // Non-null request listeners are treated as equivalent, even if they're not equal.
            new SingleRequestBuilder().setRequestListener(mock(RequestListener.class)))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setRequestListener(null),
            new SingleRequestBuilder().setRequestListener(null))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setOverrideHeight(500),
            new SingleRequestBuilder().setOverrideHeight(500))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setOverrideWidth(500),
            new SingleRequestBuilder().setOverrideWidth(500))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setModel(12345),
            new SingleRequestBuilder().setModel(12345))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setModel(null),
            new SingleRequestBuilder().setModel(null))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setErrorDrawable(new ColorDrawable(Color.GRAY)),
            new SingleRequestBuilder().setErrorDrawable(new ColorDrawable(Color.GRAY)))
        .addEquivalenceGroup(
            new SingleRequestBuilder().setPriority(Priority.LOW),
            new SingleRequestBuilder().setPriority(Priority.LOW))
        .test();
  }

  static final class SingleRequestBuilder {
    private Engine engine = mock(Engine.class);
    private Number model = 123456;
    @SuppressWarnings("unchecked")
    private Target<List> target = mock(Target.class);
    private Resource<List> resource = mockResource();
    private RequestCoordinator requestCoordinator = mock(RequestCoordinator.class);
    private Drawable placeholderDrawable = null;
    private Drawable errorDrawable = null;
    private Drawable fallbackDrawable = null;
    @SuppressWarnings("unchecked")
    private RequestListener<List> requestListener = mock(RequestListener.class);
    @SuppressWarnings("unchecked")
    private final TransitionFactory<List> transitionFactory = mock(TransitionFactory.class);
    private int overrideWidth = -1;
    private int overrideHeight = -1;
    private List<?> result = new ArrayList<>();
    private final GlideContext glideContext = mock(GlideContext.class);
    private final Key signature = new ObjectKey(12345);
    private Priority priority = Priority.HIGH;
    private boolean useUnlimitedSourceGeneratorsPool = false;
    private final Class<List> transcodeClass = List.class;
    private final Map<Class<?>, Transformation<?>> transformations = new HashMap<>();

    SingleRequestBuilder() {
      when(requestCoordinator.canSetImage(any(Request.class))).thenReturn(true);
      when(requestCoordinator.canNotifyCleared(any(Request.class))).thenReturn(true);
      when(requestCoordinator.canNotifyStatusChanged(any(Request.class))).thenReturn(true);
      when(resource.get()).thenReturn(result);
    }

    SingleRequestBuilder setEngine(Engine engine) {
      this.engine = engine;
      return this;
    }

    SingleRequestBuilder setModel(Number model) {
      this.model = model;
      return this;
    }

    SingleRequestBuilder setTarget(Target<List> target) {
      this.target = target;
      return this;
    }

    SingleRequestBuilder setResource(Resource<List> resource) {
      this.resource = resource;
      return this;
    }

    SingleRequestBuilder setRequestCoordinator(RequestCoordinator requestCoordinator) {
      this.requestCoordinator = requestCoordinator;
      return this;
    }

    SingleRequestBuilder setPlaceholderDrawable(Drawable placeholderDrawable) {
      this.placeholderDrawable = placeholderDrawable;
      return this;
    }

    SingleRequestBuilder setErrorDrawable(Drawable errorDrawable) {
      this.errorDrawable = errorDrawable;
      return this;
    }

    SingleRequestBuilder setFallbackDrawable(Drawable fallbackDrawable) {
      this.fallbackDrawable = fallbackDrawable;
      return this;
    }

    SingleRequestBuilder setRequestListener(RequestListener<List> requestListener) {
      this.requestListener = requestListener;
      return this;
    }

    SingleRequestBuilder setOverrideWidth(int overrideWidth) {
      this.overrideWidth = overrideWidth;
      return this;
    }

    SingleRequestBuilder setOverrideHeight(int overrideHeight) {
      this.overrideHeight = overrideHeight;
      return this;
    }

    SingleRequestBuilder setResult(List<?> result) {
      this.result = result;
      return this;
    }

    SingleRequestBuilder setPriority(Priority priority) {
      this.priority = priority;
      return this;
    }

    SingleRequestBuilder setUseUnlimitedSourceGeneratorsPool(
        boolean useUnlimitedSourceGeneratorsPool) {
      this.useUnlimitedSourceGeneratorsPool = useUnlimitedSourceGeneratorsPool;
      return this;
    }

    SingleRequest<List> build() {
      RequestOptions requestOptions = new RequestOptions()
          .error(errorDrawable)
          .placeholder(placeholderDrawable)
          .fallback(fallbackDrawable)
          .override(overrideWidth, overrideHeight)
          .priority(priority)
          .signature(signature)
          .useUnlimitedSourceGeneratorsPool(useUnlimitedSourceGeneratorsPool);
      return SingleRequest.obtain(
          /*context=*/glideContext,
          /*glideContext=*/glideContext,
          model,
          transcodeClass,
          requestOptions,
          overrideWidth,
          overrideHeight,
          priority,
          target,
          /*targetListener=*/ null,
          requestListener,
          requestCoordinator,
          engine,
          transitionFactory);
    }
  }

  // TODO do we want to move these to Util?
  @SuppressWarnings("unchecked")
  private static <T> Transition<T> mockTransition() {
    return mock(Transition.class);
  }

  @SuppressWarnings("unchecked")
  private static Target<List> isAListTarget() {
    return isA(Target.class);
  }

  private static GlideException isAGlideException() {
    return isA(GlideException.class);
  }

  @SuppressWarnings("unchecked")
  private static <T> Transition<T> anyTransition() {
    return any(Transition.class);
  }

  private static class CallResourceCallback implements Answer {

    private final Resource resource;

    CallResourceCallback(Resource resource) {
      this.resource = resource;
    }

    @Override
    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      ResourceCallback cb =
          (ResourceCallback) invocationOnMock.getArguments()[
              invocationOnMock.getArguments().length
                  - 1];
      cb.onResourceReady(resource, DataSource.REMOTE);
      return null;
    }
  }

  private static class CallSizeReady implements Answer {

    private final int width;
    private final int height;

    CallSizeReady(int width, int height) {
      this.width = width;
      this.height = height;
    }

    @Override
    public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
      SizeReadyCallback cb = (SizeReadyCallback) invocationOnMock.getArguments()[0];
      cb.onSizeReady(width, height);
      return null;
    }
  }

  private static class MockTarget implements Target<List> {

    private Drawable currentPlaceholder;

    @Override
    public void onLoadCleared(@Nullable Drawable placeholder) {
      currentPlaceholder = placeholder;
    }

    @Override
    public void onLoadStarted(@Nullable Drawable placeholder) {
      currentPlaceholder = placeholder;

    }

    @Override
    public void onLoadFailed(@Nullable Drawable errorDrawable) {
      currentPlaceholder = errorDrawable;

    }

    @Override
    public void onResourceReady(@NonNull List resource,
        @Nullable Transition<? super List> transition) {
      currentPlaceholder = null;
    }


    @Override
    public void getSize(@NonNull SizeReadyCallback cb) {
    }

    @Override
    public void removeCallback(@NonNull SizeReadyCallback cb) {
      // Do nothing.
    }

    @Override
    public void setRequest(@Nullable Request request) {
    }

    @Nullable
    @Override
    public Request getRequest() {
      return null;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {

    }
  }
}
