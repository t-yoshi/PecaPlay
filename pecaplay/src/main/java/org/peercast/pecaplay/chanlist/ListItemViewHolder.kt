package org.peercast.pecaplay.chanlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.peercast.pecaplay.databinding.ChDefaultBinding
import org.peercast.pecaplay.databinding.ChNgBinding


abstract class ListItemViewHolder private constructor(
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {

    abstract fun setViewModel(viewModel: ListItemViewModel)
    abstract fun executePendingBindings()


    sealed class Factory {
        abstract fun create(
            itemView: ViewGroup,
            eventListener: ListItemEventListener,
        ): ListItemViewHolder

        object Default : Factory() {
            override fun create(
                itemView: ViewGroup,
                eventListener: ListItemEventListener,
            ): ListItemViewHolder {
                val inflater = LayoutInflater.from(itemView.context)
                val binding = ChDefaultBinding.inflate(inflater, itemView, false)
                return object : ListItemViewHolder(binding.root) {
                    init {
                        //OnCheckedChangeListenerはコードからの変更もイベント発行されるので
                        binding.vStarred.setOnClickListener {
                            val vm = checkNotNull(binding.viewModel)
                            eventListener.onStarClicked(
                                vm, !vm.isStarChecked, bindingAdapterPosition
                            )
                        }
                        binding.root.setOnClickListener {
                            eventListener.onItemClick(
                                checkNotNull(binding.viewModel),
                                bindingAdapterPosition
                            )
                        }
                        binding.root.setOnLongClickListener {
                            eventListener.onItemLongClick(
                                checkNotNull(binding.viewModel), bindingAdapterPosition
                            )
                        }
                    }

                    override fun setViewModel(viewModel: ListItemViewModel) {
                        binding.viewModel = viewModel
                    }

                    override fun executePendingBindings() {
                        binding.executePendingBindings()
                    }
                }
            }
        }

        object Ng : Factory() {
            override fun create(
                itemView: ViewGroup,
                eventListener: ListItemEventListener,
            ): ListItemViewHolder {
                val inflater = LayoutInflater.from(itemView.context)
                val binding = ChNgBinding.inflate(inflater, itemView, false)
                return object : ListItemViewHolder(binding.root) {
                    init {
                        binding.root.setOnClickListener {
                            eventListener.onItemClick(
                                checkNotNull(binding.viewModel),
                                bindingAdapterPosition
                            )
                        }
                        binding.root.setOnLongClickListener {
                            eventListener.onItemLongClick(
                                checkNotNull(binding.viewModel),
                                bindingAdapterPosition
                            )
                        }
                    }

                    override fun setViewModel(viewModel: ListItemViewModel) {
                        binding.viewModel = viewModel
                    }

                    override fun executePendingBindings() {
                        binding.executePendingBindings()
                    }
                }
            }
        }

        object NgHidden : Factory() {
            override fun create(
                itemView: ViewGroup,
                eventListener: ListItemEventListener,
            ): ListItemViewHolder {
                val v = View(itemView.context)
                v.visibility = View.GONE
                return object : ListItemViewHolder(v) {
                    override fun setViewModel(viewModel: ListItemViewModel) {
                    }

                    override fun executePendingBindings() {
                    }
                }
            }
        }
    }


}

