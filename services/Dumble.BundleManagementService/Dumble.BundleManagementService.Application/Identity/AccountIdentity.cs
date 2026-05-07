using System.Security.Cryptography;
using System.Text;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.SharedKernel.Enums;

namespace Dumble.BundleManagementService.Application.Identity;

public static class AccountIdentity
{
    private static readonly Guid Namespace = new("8a3c4e72-0c1b-4f9c-9aa6-2f6c2d4a3a51");

    public static Guid ToAccountGuid(string externalUserId)
    {
        if (string.IsNullOrWhiteSpace(externalUserId))
            throw new ArgumentException("externalUserId is required", nameof(externalUserId));

        var nsBytes = Namespace.ToByteArray();
        SwapByteOrder(nsBytes);

        var nameBytes = Encoding.UTF8.GetBytes(externalUserId);

        var input = new byte[nsBytes.Length + nameBytes.Length];
        Buffer.BlockCopy(nsBytes, 0, input, 0, nsBytes.Length);
        Buffer.BlockCopy(nameBytes, 0, input, nsBytes.Length, nameBytes.Length);

        var hash = SHA1.HashData(input);

        var guidBytes = new byte[16];
        Array.Copy(hash, 0, guidBytes, 0, 16);
        guidBytes[6] = (byte)((guidBytes[6] & 0x0F) | 0x50);
        guidBytes[8] = (byte)((guidBytes[8] & 0x3F) | 0x80);

        SwapByteOrder(guidBytes);
        return new Guid(guidBytes);
    }

    public static OwnerType ToOwnerType(UserType userType, IReadOnlyList<string> roles)
    {
        if (userType is UserType.GymOwner or UserType.Gym)
            return OwnerType.Gym;

        if (userType is UserType.Trainer)
            return OwnerType.Trainer;

        if (roles.Any(r => r.Equals("ROLE_GYM_OWNER", StringComparison.OrdinalIgnoreCase)))
            return OwnerType.Gym;

        if (roles.Any(r => r.Equals("ROLE_TRAINER", StringComparison.OrdinalIgnoreCase)))
            return OwnerType.Trainer;

        throw new UnauthorizedAccessException(
            $"Account type {userType} is not allowed to own bundles");
    }

    private static void SwapByteOrder(byte[] guid)
    {
        (guid[0], guid[3]) = (guid[3], guid[0]);
        (guid[1], guid[2]) = (guid[2], guid[1]);
        (guid[4], guid[5]) = (guid[5], guid[4]);
        (guid[6], guid[7]) = (guid[7], guid[6]);
    }
}
