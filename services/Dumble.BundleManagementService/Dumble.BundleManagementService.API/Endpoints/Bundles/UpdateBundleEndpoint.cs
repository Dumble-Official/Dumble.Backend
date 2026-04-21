using System.Net;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.CreateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class UpdateBundleEndpoint(ISender mediator) : Endpoint<UpdateBundleRequest>
{
   public override void Configure()
   {
      Put("/api/bundles");
      Claims("userId");
      Options(x => x.WithTags("Bundles")
         .Accepts<CreateBundleRequest>("multipart/form-data"));
   }

   public override async Task<object?> ExecuteAsync(UpdateBundleRequest req, CancellationToken ct)
   {
      var command = new UpdateBundleCommand(Guid.NewGuid());

      await mediator.Send(command, ct);
        
      return await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
   }
}