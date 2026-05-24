using Dumble.BundleManagementService.Application.Contracts;
using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;
using Microsoft.Extensions.Logging;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.DeleteBundle;

internal sealed class DeleteBundleCommandHandler(
    IFileService fileService,
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService,
    ILogger<DeleteBundleCommandHandler> logger) : IRequestHandler<DeleteBundleCommand>
{
    public async Task Handle(DeleteBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var currentUserAccountId = AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id));

        var isAdmin = loggedInUser.IsInRole(UserType.Admin);
        var isOwner = currentUserAccountId.Equals(bundle.OwnerId);

        // ADMIN can delete any bundle; other roles can only delete their own.
        if (!isAdmin && !isOwner)
            throw new UnauthorizedAccessException("You can only delete your own bundles");

        if (isAdmin && !isOwner)
            logger.LogWarning(
                "Admin {AdminId} deleted bundle {BundleId} owned by {OwnerId}",
                loggedInUser.Id, bundle.Id.Value, bundle.OwnerId.Value);

        var tasks = bundle.Images.Select(img => fileService.DeleteAsync(img.Value));
        await Task.WhenAll(tasks);

        bundlesRepository.Delete(bundle);
        await bundlesRepository.CompleteAsync();
    }
}
