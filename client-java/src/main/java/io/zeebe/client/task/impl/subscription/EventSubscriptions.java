package io.zeebe.client.task.impl.subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.zeebe.client.impl.Loggers;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import io.zeebe.client.event.impl.EventSubscription;
import org.slf4j.Logger;

public class EventSubscriptions<T extends EventSubscription<T>>
{
    protected static final Logger LOGGER = Loggers.SUBSCRIPTION_LOGGER;

    // topicName => partitionId => subscriberKey => subscription (subscriber keys are not guaranteed to be globally unique)
    protected Map<String, Int2ObjectHashMap<Long2ObjectHashMap<T>>> subscriptions = new HashMap<>();

    protected final List<T> pollableSubscriptions = new CopyOnWriteArrayList<>();
    protected final List<T> managedSubscriptions = new CopyOnWriteArrayList<>();

    public void addSubscription(final T subscription)
    {
        if (subscription.isManagedSubscription())
        {
            addManagedSubscription(subscription);
        }
        else
        {
            addPollableSubscription(subscription);
        }
    }

    protected void addPollableSubscription(final T subscription)
    {
        this.pollableSubscriptions.add(subscription);
    }

    protected void addManagedSubscription(final T subscription)
    {
        this.managedSubscriptions.add(subscription);
    }

    public void closeAll()
    {
        for (final T subscription : pollableSubscriptions)
        {
            closeSubscription(subscription);
        }

        for (final T subscription : managedSubscriptions)
        {
            closeSubscription(subscription);
        }
    }

    protected void closeSubscription(EventSubscription<T> subscription)
    {
        try
        {
            subscription.close();
        }
        catch (final Exception e)
        {
            LOGGER.error("Unable to close subscription with key: " + subscription.getSubscriberKey(), e);
        }
    }

    public void abortSubscriptionsOnChannel(final int channelId)
    {
        doForSubscriptionsOnChannel(pollableSubscriptions, channelId, s -> s.abortAsync());
        doForSubscriptionsOnChannel(managedSubscriptions, channelId, s -> s.abortAsync());
    }

    public void suspendSubscriptionsOnChannel(final int channelId)
    {
        doForSubscriptionsOnChannel(pollableSubscriptions, channelId, s -> s.suspendAsync());
        doForSubscriptionsOnChannel(managedSubscriptions, channelId, s -> s.suspendAsync());
    }

    public void reopenSubscriptionsOnChannel(int channelId)
    {
        doForSubscriptionsOnChannel(pollableSubscriptions, channelId, s -> s.reopenAsync());
        doForSubscriptionsOnChannel(managedSubscriptions, channelId, s -> s.reopenAsync());
    }

    protected void doForSubscriptionsOnChannel(List<T> subscriptions, int channelId, Consumer<T> action)
    {
        for (int i = 0; i < subscriptions.size(); i++)
        {
            final T subscription = subscriptions.get(i);
            if (subscription.getReceiveChannelId() == channelId)
            {
                action.accept(subscription);
            }
        }
    }

    public List<T> getManagedSubscriptions()
    {
        return managedSubscriptions;
    }

    public List<T> getPollableSubscriptions()
    {
        return pollableSubscriptions;
    }

    public void removeSubscription(final T subscription)
    {
        onSubscriptionClosed(subscription);

        pollableSubscriptions.remove(subscription);
        managedSubscriptions.remove(subscription);
    }

    public T getSubscription(final String topicName, final int partitionId, final long subscriberKey)
    {
        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = subscriptions.get(topicName);

        if (subscriptionsForTopic != null)
        {
            final Long2ObjectHashMap<T> subscriptionsForPartition = subscriptionsForTopic.get(partitionId);

            if (subscriptionsForPartition != null)
            {
                return subscriptionsForPartition.get(subscriberKey);
            }

        }

        return null;
    }

    public void onSubscriptionOpened(T subscription)
    {
        this.subscriptions
            .computeIfAbsent(subscription.getTopicName(), topicName -> new Int2ObjectHashMap<>())
            .computeIfAbsent(subscription.getPartitionId(), partitionId -> new Long2ObjectHashMap<>())
            .put(subscription.getSubscriberKey(), subscription);
    }

    public void onSubscriptionClosed(T subscription)
    {
        final String topicName = subscription.getTopicName();
        final int partitionId = subscription.getPartitionId();

        final Int2ObjectHashMap<Long2ObjectHashMap<T>> subscriptionsForTopic = subscriptions.get(topicName);
        if (subscriptionsForTopic != null)
        {
            final Long2ObjectHashMap<T> subscriptionsForPartition = subscriptionsForTopic.get(partitionId);
            if (subscriptionsForPartition != null)
            {
                subscriptionsForPartition.remove(subscription.getSubscriberKey());

                if (subscriptionsForPartition.isEmpty())
                {
                    subscriptionsForTopic.remove(partitionId);
                }

                if (subscriptionsForTopic.isEmpty())
                {
                    subscriptions.remove(topicName);
                }
            }
        }
    }

}
