// The MIT License (MIT)
//
// Copyright (c) 2017 Smart&Soft
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.smartnsoft.recyclerview.recyclerview;

/*
 * Copyright (C) 2014 I.C.N.H GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.smartnsoft.recyclerview.adapter.SmartRecyclerAdapter;

/**
 * A {@link android.support.v7.widget.RecyclerView} that provides reordering with drag&amp;drop. The Adapter has to be of type
 * {@link SmartReorderRecyclerView.ReorderAdapter}. Furthermore you have to provide stable ids
 * {@link android.support.v7.widget.RecyclerView.Adapter#setHasStableIds(boolean)}
 *
 * @author Adrien Vitti
 * @since 2015.06.01
 */
@TargetApi(Build.VERSION_CODES.FROYO)
public class SmartReorderRecyclerView
    extends RecyclerView
{

  /**
   * Special adapter that provides reorder functionality. Implementations have to provide stable ids {@link #hasStableIds()}
   */
  public static abstract class ReorderAdapter
      extends SmartRecyclerAdapter
  {

    public ReorderAdapter(Context context)
    {
      super(context);
    }

    /**
     * Swap the positions of the elements with the given indices. You don't have to notify the change. This will be handled by the recylcerview.
     * Example:
     * <p>
     * <pre>
     * {
     *   &#064;code
     *   Object temp = cheeseList.get(fromIndex);
     *   dataList.set(fromIndex, cheeseList.get(toIndex));
     *   dataList.set(toIndex, temp);
     * }
     * </pre>
     *
     * @param fromIndex the index
     * @param toIndex   the index
     */
    public abstract void swapElements(int fromIndex, int toIndex);

  }

  private static final int INVALID_POINTER_ID = -1;

  private static final int LINE_THICKNESS = 5;

  private static final int SMOOTH_SCROLL_AMOUNT_AT_EDGE = 100;

  private static final int INVALID_ID = -1;

  /**
   * This TypeEvaluator is used to animate the BitmapDrawable back to its final location when the user lifts his finger by modifying the
   * BitmapDrawable's bounds.
   */
  private final static TypeEvaluator<Rect> sBoundEvaluator = new TypeEvaluator<Rect>()
  {

    @Override
    public Rect evaluate(float fraction, Rect startValue, Rect endValue)
    {
      return new Rect(interpolate(startValue.left, endValue.left, fraction),
          interpolate(startValue.top, endValue.top, fraction), interpolate(startValue.right, endValue.right, fraction),
          interpolate(startValue.bottom, endValue.bottom, fraction));
    }

    public int interpolate(int start, int end, float fraction)
    {
      return (int) (start + fraction * (end - start));
    }

  };

  private int activePointerId = INVALID_POINTER_ID;

  private int lastEventY, lastEventX;

  private int downX;

  private int downY;

  private int totalOffsetY, totalOffsetX;

  private BitmapDrawable hoverCell;

  private Rect hoverCellOriginalBounds;

  private Rect hoverCellCurrentBounds;

  private boolean cellIsMobile = false;

  private long mobileItemId = INVALID_ID;

  private int smoothScrollAmountAtEdge;

  private boolean usWaitingForScrollFinish;

  private int borderColor = -1;

  private long aboveItemId = INVALID_ID;

  private long belowItemId = INVALID_ID;

  public SmartReorderRecyclerView(Context context)
  {
    super(context);
    init(context);
  }

  public SmartReorderRecyclerView(Context context, AttributeSet attrs)
  {
    super(context, attrs);
    init(context);
  }

  public SmartReorderRecyclerView(Context context, AttributeSet attrs, int defStyle)
  {
    super(context, attrs, defStyle);
    init(context);
  }

  @Override
  public void setAdapter(Adapter adapter)
  {
    if ((adapter instanceof ReorderAdapter) == false || adapter.hasStableIds() == false)
    {
      throw new IllegalStateException("ReorderRecyclerView only works with ReorderAdapter and must have stable ids!");
    }

    super.setAdapter(adapter);
  }

  /**
   * dispatchDraw gets invoked when all the child views are about to be drawn. By overriding this method, the hover cell (BitmapDrawable) can be drawn
   * over the recyclerviews's items whenever the recyclerviews is redrawn.
   */
  @Override
  protected void dispatchDraw(@NonNull Canvas canvas)
  {
    super.dispatchDraw(canvas);
    if (hoverCell != null)
    {
      hoverCell.draw(canvas);
    }
  }

  public void init(Context context)
  {
    final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    smoothScrollAmountAtEdge = (int) (SMOOTH_SCROLL_AMOUNT_AT_EDGE / metrics.density);

    // detector for the long press in order to start the dragging
    final GestureDetector longPressGestureDetector = new GestureDetector(context,
        new GestureDetector.SimpleOnGestureListener()
        {

          @Override
          public void onLongPress(MotionEvent event)
          {
            downX = (int) event.getX();
            downY = (int) event.getY();
            activePointerId = event.getPointerId(0);

            totalOffsetY = 0;
            totalOffsetX = 0;
            final View selectedView = findChildViewUnder(downX, downY);
            if (selectedView == null)
            {
              return;
            }

            mobileItemId = getChildItemId(selectedView);
            hoverCell = getAndAddHoverView(selectedView);
            updateNeighborViewsForID(mobileItemId);
            selectedView.setVisibility(INVISIBLE);
            cellIsMobile = true;
          }
        });

    final OnItemTouchListener itemTouchListener = new OnItemTouchListener()
    {
      @Override
      public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event)
      {
        if (longPressGestureDetector.onTouchEvent(event))
        {
          return true;
        }

        switch (event.getAction())
        {
          case MotionEvent.ACTION_MOVE:
            return cellIsMobile;
          default:
            break;
        }

        return false;
      }

      @Override
      public void onTouchEvent(RecyclerView rv, MotionEvent event)
      {
        handleMotionEvent(event);
      }

      @Override
      public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept)
      {

      }
    };
    addOnItemTouchListener(itemTouchListener);
  }

  /**
   * Retrieves the position in the list corresponding to itemID
   */
  public int getPositionForID(long itemID)
  {
    ViewHolder v = findViewHolderForItemId(itemID);
    if (v != null && v.itemView != null)
    {
      return getChildAdapterPosition(v.itemView);
    }
    else
    {
      return -1;
    }
  }

  /**
   * This method is in charge of determining if the hover cell is above/below or left/right the bounds of the recyclerview. If so, the recyclerview
   * does an appropriate upward or downward smooth scroll so as to reveal new items.
   */
  public boolean handleMobileCellScroll(Rect r)
  {
    if (getLayoutManager().canScrollVertically())
    {
      int offset = computeVerticalScrollOffset();
      int height = getHeight();
      int extent = computeVerticalScrollExtent();
      int range = computeVerticalScrollRange();
      int hoverViewTop = r.top;
      int hoverHeight = r.height();

      if (hoverViewTop <= 0 && offset > 0)
      {
        scrollBy(0, -smoothScrollAmountAtEdge);

        return true;
      }

      if (hoverViewTop + hoverHeight >= height && (offset + extent) < range)
      {
        scrollBy(0, smoothScrollAmountAtEdge);

        return true;
      }
    }

    if (getLayoutManager().canScrollHorizontally())
    {
      int offset = computeHorizontalScrollOffset();
      int width = getWidth();
      int extent = computeHorizontalScrollExtent();
      int range = computeHorizontalScrollRange();
      int hoverViewLeft = r.left;
      int hoverWidth = r.width();

      if (hoverViewLeft <= 0 && offset > 0)
      {
        scrollBy(-smoothScrollAmountAtEdge, 0);

        return true;
      }

      if (hoverViewLeft + hoverWidth >= width && (offset + extent) < range)
      {
        scrollBy(smoothScrollAmountAtEdge, 0);

        return true;
      }
    }

    return false;
  }

  public void setBorderColor(int colorRes)
  {
    borderColor = colorRes;
  }

  /**
   * Creates the hover cell with the appropriate bitmap and of appropriate size. The hover cell's BitmapDrawable is drawn on top of the bitmap every
   * single time an invalidate call is made.
   *
   * @param view The view which will be used to create the hover
   * @return A drawable with the size of the view
   */
  protected BitmapDrawable getAndAddHoverView(View view)
  {
    int width = view.getWidth();
    int height = view.getHeight();
    int top = view.getTop();
    int left = view.getLeft();

    final Bitmap bitmap = getBitmapWithBorder(view);

    final BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);

    hoverCellOriginalBounds = new Rect(left, top, left + width, top + height);
    hoverCellCurrentBounds = new Rect(hoverCellOriginalBounds);

    drawable.setBounds(hoverCellCurrentBounds);

    return drawable;
  }

  /**
   * Draws a black border over the screenshot of the view passed in.
   *
   * @param view The view on which will be based the black border
   * @return A bitmap of the view with black border
   */
  protected Bitmap getBitmapWithBorder(View view)
  {
    final Bitmap bitmap = getBitmapFromView(view);
    final Canvas can = new Canvas(bitmap);

    final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

    final Paint paint = new Paint();
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(LINE_THICKNESS);
    if (borderColor != -1)
    {
      paint.setColor(borderColor);
    }
    else
    {
      paint.setColor(Color.BLACK);
    }

    can.drawBitmap(bitmap, 0, 0, null);
    can.drawRect(rect, paint);

    return bitmap;
  }

  /**
   * Returns a bitmap showing a screenshot of the view passed in.
   */
  protected Bitmap getBitmapFromView(View v)
  {
    final Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
    final Canvas canvas = new Canvas(bitmap);
    v.draw(canvas);

    return bitmap;
  }

  private void handleMotionEvent(MotionEvent event)
  {
    switch (event.getAction())
    {
      case MotionEvent.ACTION_MOVE:

        if (activePointerId == INVALID_POINTER_ID)
        {
          break;
        }

        int pointerIndex = event.findPointerIndex(activePointerId);

        lastEventY = (int) event.getY(pointerIndex);
        // lastEventX = (int) event.getX(pointerIndex);
        final int deltaY = lastEventY - downY;
        // NOTE: DeltaX = 0 because we don't want to be able to move item horizontally
        final int deltaX = 0; // lastEventX - downX;

        if (cellIsMobile)
        {
          hoverCellCurrentBounds.offsetTo(hoverCellOriginalBounds.left + deltaX + totalOffsetX,
              hoverCellOriginalBounds.top + deltaY + totalOffsetY);
          if (hoverCell != null)
          {
            hoverCell.setBounds(hoverCellCurrentBounds);
          }
          invalidate();

          handleCellSwitch();

          handleMobileCellScroll();
        }
        break;
      case MotionEvent.ACTION_UP:
        touchEventsEnded();
        break;
      case MotionEvent.ACTION_CANCEL:
        touchEventsCancelled();
        break;
      case MotionEvent.ACTION_POINTER_UP:
      /*
       * If a multitouch event took place and the original touch dictating the movement of the hover cell has ended, then the dragging event ends and
       * the hover cell is animated to its corresponding position in the listview.
       */
        pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        final int pointerId = event.getPointerId(pointerIndex);
        if (pointerId == activePointerId)
        {
          touchEventsEnded();
        }
        break;
      default:
        break;
    }
  }

  /**
   * This method determines whether the hover cell has been shifted far enough to invoke a cell swap. If so, then the respective cell swap candidate
   * is determined and the data set is changed. Upon posting a notification of the data set change, a layout is invoked to place the cells in the
   * right place.
   */
  private void handleCellSwitch()
  {
    final ViewHolder mobileViewHolder = findViewHolderForItemId(mobileItemId);
    final View mobileView = mobileViewHolder != null ? mobileViewHolder.itemView : null;
    if (mobileView != null)
    {
      final ViewHolder belowViewHolder = findViewHolderForItemId(belowItemId);
      final View belowView = belowViewHolder != null ? belowViewHolder.itemView : null;
      final ViewHolder aboveViewHolder = findViewHolderForItemId(aboveItemId);
      final View aboveView = aboveViewHolder != null ? aboveViewHolder.itemView : null;

      final int deltaY = lastEventY - downY;
      int deltaYTotal = hoverCellOriginalBounds.top + totalOffsetY + deltaY;
      boolean isBelow = (belowView != null) && (deltaYTotal > belowView.getTop());
      boolean isAbove = (aboveView != null) && (deltaYTotal < aboveView.getTop());

      if (isBelow || isAbove)
      {
        if ((isBelow && belowView == null) || (isAbove && aboveView == null))
        {
          updateNeighborViewsForID(mobileItemId);

          return;
        }

        final int childPosition = getChildAdapterPosition(isBelow ? belowView : aboveView);
        final int originalItem = getChildAdapterPosition(mobileView);
        swapElements(originalItem, childPosition);
        updateNeighborViewsForID(mobileItemId);
      }
    }
  }

  /**
   * Stores a reference to the views above and below the item currently corresponding to the hover cell. It is important to note that if this item is
   * either at the top or bottom of the list, aboveItemId or belowItemId may be invalid.
   */
  private void updateNeighborViewsForID(long itemID)
  {
    final int position = getPositionForID(itemID);
    final ReorderAdapter adapter = (ReorderAdapter) getAdapter();

    // If the mobile item is the first item, set the above item to invalid ID
    if (position - 1 >= 0)
    {
      aboveItemId = adapter.getItemId(position - 1);
    }
    else
    {
      aboveItemId = INVALID_ID;
    }

    // If the mobile item is the last item, set the above item to invalid ID
    if (position + 1 < getAdapter().getItemCount())
    {
      belowItemId = adapter.getItemId(position + 1);
    }
    else
    {
      belowItemId = INVALID_ID;
    }
  }

  /**
   * Swaps the the elements with the given indices.
   *
   * @param fromIndex the from-element index
   * @param toIndex   the to-element index
   */
  private void swapElements(int fromIndex, int toIndex)
  {
    final ReorderAdapter adapter = (ReorderAdapter) getAdapter();
    adapter.swapElements(fromIndex, toIndex);
  }

  /**
   * Resets all the appropriate fields to a default state while also animating the hover cell back to its correct location.
   */
  private void touchEventsEnded()
  {
    final ViewHolder viewHolderForItemId = findViewHolderForItemId(mobileItemId);
    if (viewHolderForItemId == null)
    {
      return;
    }

    final View mobileView = viewHolderForItemId.itemView;
    if (cellIsMobile || usWaitingForScrollFinish)
    {
      cellIsMobile = false;
      usWaitingForScrollFinish = false;
      activePointerId = INVALID_POINTER_ID;

      // If the autoscroller has not completed scrolling, we need to wait for it to
      // finish in order to determine the final location of where the hover cell
      // should be animated to.
      if (getScrollState() != SCROLL_STATE_IDLE)
      {
        usWaitingForScrollFinish = true;
        return;
      }

      hoverCellCurrentBounds.offsetTo(mobileView.getLeft(), mobileView.getTop());

      final ObjectAnimator hoverViewAnimator = ObjectAnimator.ofObject(hoverCell, "bounds", sBoundEvaluator,
          hoverCellCurrentBounds);
      hoverViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener()
      {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator)
        {
          invalidate();
        }
      });
      hoverViewAnimator.addListener(new AnimatorListenerAdapter()
      {
        @Override
        public void onAnimationStart(Animator animation)
        {
          setEnabled(false);
        }

        @Override
        public void onAnimationEnd(Animator animation)
        {
          mobileItemId = INVALID_ID;
          mobileView.setVisibility(VISIBLE);
          hoverCell = null;
          setEnabled(true);
          invalidate();
        }
      });
      hoverViewAnimator.start();
    }
    else
    {
      touchEventsCancelled();
    }
  }

  /**
   * Resets all the appropriate fields to a default state.
   */
  private void touchEventsCancelled()
  {
    final ViewHolder viewHolderForItemId = findViewHolderForItemId(mobileItemId);
    if (viewHolderForItemId == null)
    {
      return;
    }

    final View mobileView = viewHolderForItemId.itemView;
    if (cellIsMobile)
    {
      mobileItemId = INVALID_ID;
      mobileView.setVisibility(VISIBLE);
      hoverCell = null;
      invalidate();
    }
    cellIsMobile = false;
    activePointerId = INVALID_POINTER_ID;
  }

  /**
   * Determines whether this recyclerview is in a scrolling state invoked by the fact that the hover cell is out of the bounds of the recyclerview;
   */
  private void handleMobileCellScroll()
  {
    handleMobileCellScroll(hoverCellCurrentBounds);
  }

}