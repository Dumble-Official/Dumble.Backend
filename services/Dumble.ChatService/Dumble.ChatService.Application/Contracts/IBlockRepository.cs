namespace Dumble.ChatService.Application.Contracts;

public interface IBlockRepository
{
    Task BlockAsync(string blockerId, string blockedId, CancellationToken ct = default);
    Task UnblockAsync(string blockerId, string blockedId, CancellationToken ct = default);

    /// <summary>True if either user has blocked the other — used to stop messaging both ways.</summary>
    Task<bool> IsBlockedBetweenAsync(string userA, string userB, CancellationToken ct = default);

    /// <summary>Ids the user has blocked.</summary>
    Task<List<string>> GetBlockedIdsAsync(string blockerId, CancellationToken ct = default);
}
