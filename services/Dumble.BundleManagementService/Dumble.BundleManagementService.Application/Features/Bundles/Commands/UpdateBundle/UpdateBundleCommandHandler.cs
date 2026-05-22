using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Application.Identity;
using Dumble.BundleManagementService.Domain.BundleAggregate;
using Dumble.BundleManagementService.Domain.BundleAggregate.Enums;
using Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using Dumble.SharedKernel.Contracts;
using Dumble.SharedKernel.Enums;
using MediatR;
using Name = Dumble.BundleManagementService.Domain.BundleAggregate.ValueObjects.Name;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;

internal sealed class UpdateBundleCommandHandler(
    IGenericRepository<Bundle, BundleId> bundlesRepository,
    ILoggedInUserService loggedInUserService) : IRequestHandler<UpdateBundleCommand>
{
    public async Task Handle(UpdateBundleCommand request, CancellationToken cancellationToken)
    {
        var loggedInUser = loggedInUserService.GetCurrentUser();

        var bundle = await bundlesRepository.Get(BundleId.Create(request.Id))
            ?? throw new KeyNotFoundException($"Bundle {request.Id} not found");

        var currentUserAccountId = AccountId.Create(AccountIdentity.ToAccountGuid(loggedInUser.Id));

        // ADMIN can update any bundle; other roles can only update their own.
        if (!loggedInUser.IsInRole(UserType.Admin) && !currentUserAccountId.Equals(bundle.OwnerId))
            throw new UnauthorizedAccessException("You can only update your own bundles");

        // Apply each non-null field through the aggregate's Modify method.
        // The previous implementation called Update(bundle) on an unchanged
        // entity and every PUT silently no-op'd, so clients saw 204 while
        // the database row stayed the same — the entire update flow was broken.
        Status? statusValue = null;
        if (!string.IsNullOrWhiteSpace(request.Status))
        {
            if (!Enum.TryParse<Status>(request.Status, ignoreCase: true, out var parsed))
                throw new ArgumentException($"Unknown bundle status '{request.Status}'");
            statusValue = parsed;
        }

        bundle.Modify(
            modifier: currentUserAccountId,
            name: request.Name is null ? null : Name.Create(request.Name),
            description: request.Description is null ? null : Description.Create(request.Description),
            price: request.Price is null ? null : Price.Create(request.Price.Value),
            status: statusValue,
            expiresOn: request.ExpiresOn,
            categoryId: request.CategoryId is null ? null : CategoryId.Create(request.CategoryId.Value));

        bundlesRepository.Update(bundle);
        await bundlesRepository.CompleteAsync();
    }
}
