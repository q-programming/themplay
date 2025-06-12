package pl.qprogramming.themplay.views;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class ItemMoveCallback<T extends RecyclerView.ViewHolder> extends ItemTouchHelper.Callback {

    private final ItemTouchHelperContract<T> mAdapter;

    public ItemMoveCallback(ItemTouchHelperContract<T> adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }


    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {

    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target) {
        mAdapter.onRowMoved(viewHolder.getAbsoluteAdapterPosition(), target.getAbsoluteAdapterPosition());
        return true;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder,
                                  int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            T specificViewHolder = (T) viewHolder;
            mAdapter.onRowSelected(specificViewHolder);
        }

        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        T specificViewHolder = (T) viewHolder;
        mAdapter.onRowClear(specificViewHolder);

    }

    public interface ItemTouchHelperContract<T extends RecyclerView.ViewHolder> {

        void onRowMoved(int fromPosition, int toPosition);

        void onRowSelected(T myViewHolder);

        void onRowClear(T myViewHolder);

    }

}
