package com.smartnsoft.recyclerview.wrapper;

import android.support.v7.util.DiffUtil;

import com.smartnsoft.recyclerview.adapter.DiffUtilSmartRecyclerAdapter;
import com.smartnsoft.recyclerview.adapter.DiffUtilSmartRecyclerAdapter.SmartDiffUtilCallback;

/**
 * Wrappers use in the {@link DiffUtilSmartRecyclerAdapter} class have to implement this interface
 *
 * @author Ludovic Roland
 * @since 2017.09.27
 */

public interface SmartDiffUtil
{

  /**
   * Calls into the {@link SmartDiffUtilCallback#areItemsTheSame(int, int)} methods to decide whether two object represent the same Item.
   *
   * @return the id of the item
   * @see DiffUtil.Callback#areItemsTheSame(int, int)
   */
  long getDiffUtilId();

  /**
   * Calls into the {@link SmartDiffUtilCallback#areContentsTheSame(int, int)} methods to to check whether two items have the same data.
   *
   * @return the hashcode of the item
   * @see DiffUtil.Callback#areContentsTheSame(int, int)
   */
  long getDiffUtilHashCode();

}
