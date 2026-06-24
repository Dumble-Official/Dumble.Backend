using Dumble.ChatService.Application.Contracts;
using Dumble.ChatService.Domain.Models;

namespace Dumble.ChatService.Application.Common;

/// <summary>
/// Heals participant identities stored as a placeholder. CreateConversation records
/// non-creator participants with <c>DisplayName == UserId</c> and no photo — the creator
/// only has their own JWT claims, not the peer's. This backfills the real name/photo from
/// the most recent message that participant authored, so existing conversations resolve to
/// real names on read instead of waiting for the peer to send another message after deploy.
/// </summary>
public static class ParticipantBackfill
{
    // Direct chats need only the peer; group chats almost always surface a recent speaker
    // within this window. Anyone who never spoke is resolved later by SendMessage's upsert.
    private const int RecentMessageWindow = 200;

    /// <summary>
    /// Fills any placeholder participant names from message history (mutates
    /// <paramref name="conversation"/> in place). Returns <c>true</c> when something changed
    /// so the caller can persist the corrected document.
    /// </summary>
    public static async Task<bool> ResolveSentinelNamesAsync(
        Conversation conversation,
        IMessageRepository messageRepository,
        CancellationToken ct)
    {
        var sentinels = conversation.Participants.Where(IsSentinel).ToList();
        if (sentinels.Count == 0)
            return false;

        var messages = await messageRepository.GetByConversationIdAsync(
            conversation.Id, null, RecentMessageWindow, ct);
        if (messages.Count == 0)
            return false;

        var changed = false;
        foreach (var p in sentinels)
        {
            // Messages come newest-first, so the first match is the freshest identity.
            var msg = messages.FirstOrDefault(m =>
                m.SenderId == p.UserId &&
                !string.IsNullOrWhiteSpace(m.SenderName) &&
                m.SenderName != p.UserId);

            if (msg is null)
                continue;

            p.DisplayName = msg.SenderName;
            p.ProfileImage = msg.SenderProfileImage;
            changed = true;
        }

        return changed;
    }

    private static bool IsSentinel(Participant p) =>
        string.IsNullOrWhiteSpace(p.DisplayName) || p.DisplayName == p.UserId;
}
